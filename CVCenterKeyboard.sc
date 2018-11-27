CVCenterKeyboard {
	classvar <allKeyboards;
	var <synthDefName, <outArg, <keyboardArg, <velocArg, <bendArg, <widgetsPrefix, <>srcID;
	var <>bendSpec, <>out, <server;
	var <wdgtNames, <outProxy;
	var <sample = false, <sampleEvents, <pdef;
	var on, off, bend, namesCVs, onTimes, offTimes, sampleStart, sampleEnd;
	var <>debug = false;

	*new { |synthDefName, outArg = \out, keyboardArg = \freq, velocArg = \veloc, bendArg = \bend, widgetsPrefix = \kb, srcID, connectMidi = true|
		^super.newCopyArgs(synthDefName, outArg, keyboardArg, velocArg, bendArg, widgetsPrefix, srcID).init(connectMidi);
	}

	init { |connectMidi|
		synthDefName = synthDefName.asSymbol;

		SynthDescLib.at(synthDefName) ?? {
			Error(
				"The SynthDef '%' does not exist".format(synthDefName)
			).throw;
		};

		if (SynthDescLib.at(synthDefName).hasGate.not) {
			Error(
				"The given SynthDef does not provide a 'gate' argument and can not be used."
			).throw;
		};

		allKeyboards ?? {
			allKeyboards = ();
		};

		allKeyboards.put(synthDefName, this);

		this.bendSpec ?? {
			this.bendSpec = ControlSpec(0.midicps.neg, 0.midicps, \lin, 0, 0, " hz");
		};

		if (CVCenter.scv[synthDefName].isNil) {
			CVCenter.scv.put(synthDefName, Array.newClear(128));
			MIDIClient.init;
			// doesn't seem to work properly on Ubuntustudio 16
			// possibly has to be done manually in QJackQtl...
			if (connectMidi) {
				try { MIDIIn.connectAll } { |error|
					error.postln;
					"MIDIIn.connectAll failed. Please establish the necessary connections manually".warn;
				}
			}
		} {
			"A keyboard for the SynthDef '%' has already been initialized".format(synthDefName).warn;
		}
	}

	// keyboardArg is the arg that will be set through playing the keyboard
	// bendArg will be the arg that's set through the pitch bend wheel
	setUpControls { |tab, prefix, outControl, keyboardControl, velocControl, bendControl, theServer, out=0, deactivateDefaultWidgetActions = true, srcID|
		var testSynth, notesEnv;
		var args = [];

		if (theServer.isNil) {
			server = Server.default
		} {
			server = theServer;
		};

		prefix !? { widgetsPrefix = prefix };
		outControl !? { outArg = outControl };
		keyboardControl !? { keyboardArg = keyboardControl };
		velocControl !? { velocArg = velocControl };
		bendControl !? { bendArg = bendControl };
		srcID !? { this.srcID = srcID };

		this.out ?? { this.out = out };

		tab ?? { tab = synthDefName };

		server.waitForBoot {
			// SynthDef *should* have an \amp arg, otherwise it will sound for moment
			testSynth = Synth(synthDefName);
			// \gate will be set internally
			testSynth.cvcGui(prefix: widgetsPrefix, excemptArgs: [outArg, keyboardArg, velocArg, \gate], tab: tab, completionFunc: {
				this.prAddWidgetActionsForKeyboard(deactivateDefaultWidgetActions);
			});
			testSynth.release;
		}
	}

	// private
	prAddWidgetActionsForKeyboard { |deactivateDefaultActions|
		var args = SynthDescLib.at(synthDefName).controlDict.keys.asArray;
		var wdgtName, nameString;

		this.prInitCVs(args);

		args.do { |name, i|
			wdgtName = wdgtNames[i];
			CVCenter.cvWidgets[wdgtName] !? {
				if (CVCenter.cvWidgets[wdgtName].class == CVWidget2D) {
					#[lo, hi].do { |slot|
						CVCenter.addActionAt(
							wdgtName, 'keyboard set arg',
							"{ |cv| CVCenter.scv['%'].do { |synth| synth !? { synth.set('%', cv.value) }}; }"
							.format(synthDefName, name), slot);
						CVCenter.activateActionAt(wdgtName, \default, deactivateDefaultActions.not, slot);
					}
				} {
					CVCenter.addActionAt(wdgtName, 'keyboard set arg',
						"{ |cv| CVCenter.scv['%'].do { |synth| synth !? { synth.set('%', cv.value) }}; }"
						.format(synthDefName, name));
					CVCenter.activateActionAt(wdgtName, \default, deactivateDefaultActions.not);
				}
			}
		};

		this.prInitKeyboard;
	}

	reInit {
		var args = SynthDescLib.at(synthDefName).controlDict.keys.asArray;
		CVCenter.scv[synthDefName] !? {
			"re-initializing!".postln;
			this.free;
			CVCenter.scv.put(synthDefName, Array.newClear(128));
			this.prInitCVs(args);
			this.prInitKeyboard;
		}
	}

	// private
	prInitCVs { |args|
		var nameString, wdgtName;

		#wdgtNames, namesCVs = []!2;

		args.do { |name|
			nameString = name.asString;
			widgetsPrefix.notNil !? {
				nameString = nameString[0].toUpper ++ nameString[1..nameString.size-1];
			};
			wdgtName = (widgetsPrefix ++ nameString).asSymbol;
			CVCenter.cvWidgets[wdgtName] !? {
				if (CVCenter.cvWidgets[wdgtName].class == CVWidget2D) {
					if (namesCVs.includes(name).not) {
						namesCVs = namesCVs.add(name).add(CVCenter.at(wdgtName).asArray);
					}
				} {
					if (namesCVs.includes(name).not) {
						namesCVs = namesCVs.add(name).add(CVCenter.at(wdgtName));
					}
				}
			};
			wdgtNames = wdgtNames.add(wdgtName);
		};
	}

	// private
	prInitKeyboard {
		var onTime, offTime;
		on = MIDIFunc.noteOn({ |veloc, num, chan, src|
			var argsValues = [keyboardArg, num.midicps, velocArg, veloc * 0.005, outArg, this.out] ++ namesCVs.deepCollect(2, _.value);
			if (this.debug) { "on[num: %]: %\n\nchan: %, src: %\n".postf(num, argsValues, chan, src) };
			if (srcID.isNil or: { this.srcID.notNil and: this.srcID == src }) {
				CVCenter.scv[synthDefName][num] = Synth(synthDefName, argsValues);
			};
			if (sample) {
				onTimes[num] = Main.elapsedTime;
				argsValues.pairsDo { |k, v|
					sampleEvents[num][k] ?? {
						/*if (v.size > 1) {
							sampleEvents[num].put(k, [[]]);
						} {*/
							sampleEvents[num].put(k, [])
						// }
					};
					/*if (v.size > 1) {
						[k, v, num, sampleEvents[num][k]].postln;
						sampleEvents[num][k][0] = sampleEvents[num][k][0].add(v);
					} {*/
						sampleEvents[num][k] = sampleEvents[num][k].add(v);
					// };
				};
				sampleEvents[num].dur ?? {
					sampleEvents[num].put(\dur, []);
				};
				sampleEvents[num].dur = sampleEvents[num].dur.add(Rest(onTimes[num] - offTimes[num]));
			}
		});

		off = MIDIFunc.noteOff({ |veloc, num, chan, src|
			if (this.debug) { "off[num: %]\n".postf(num) };
			if (srcID.isNil or: { this.srcID.notNil and: this.srcID == src }) {
				CVCenter.scv[synthDefName][num].release;
			};
			if (sample) {
				offTimes[num] = Main.elapsedTime;
				sampleEvents[num].dur ?? {
					sampleEvents[num].put(\dur, []);
				};
				sampleEvents[num].dur = sampleEvents[num].dur.add(offTimes[num] - onTimes[num]);
			}
		});

		bend = MIDIFunc.bend({ |bendVal, chan, src|
			if (this.debug) { "bend: %\n".postf(bendVal) };
			if (srcID.isNil or: { this.srcID.notNil and: this.srcID == src }) {
				CVCenter.scv[synthDefName].do({ |synth, i|
					synth.set(bendArg, (i + bendSpec.map(bendVal / 16383)).midicps)
				})
			}
		});
	}

	addOutProxy { |numChannels=2, useNdef=false|
		var name = ("synthDefName" ++ "Out").asSymbol;
		this.out_(Bus.audio(server, numChannels));
		if (useNdef.not) {
			outProxy = NodeProxy.audio(server, numChannels);
		} {
			Ndef(name).mold(numChannels, \audio, \elastic);
			outProxy = Ndef(name);
		};
		outProxy.source = {
			In.ar(this.out, numChannels)
		};
		outProxy.play;
	}

	removeOutProxy { |out=0|
		this.out_(out);
		outProxy.clear;
	}

	free {
		on !? { on.free };
		off !? { off.free };
		bend !? { bend.free };
		CVCenter.scv[synthDefName].do(_.release);
		CVCenter.scv.removeAt(synthDefName);
	}

	sample_ { |onOff|
		var pbinds, items, pbproxy, last;
		sample = onOff;
		if (sample == false) {
			"sample turned off, should start playing now".postln;
			pdef ?? { pdef = [] };
			sampleEnd = Main.elapsedTime;
			sampleEvents.do { |e, num|
				// add last event - not considered within noteOn, notOff
				e.dur !? {
					// [num, this.prDurSum(e.dur)].postln;
					if (e.dur.last.isRest) {
						last = sampleEnd - onTimes[num];
					} {
						last = Rest(sampleEnd - offTimes[num]);
					};
					e.dur = e.dur.add(last);
					// [num, this.prDurSum(e.dur)].postln;
				}
			};
			pbinds = sampleEvents.collect { |slot, num|
				if (slot.isEmpty.not) {
					items = [\instrument, (synthDefName ++ "_mono").asSymbol, keyboardArg, num.midicps]
					++ slot.collect(Pseq(_, inf)).asPairs;
					// items.postcs; "\n\n".postln;
					pbproxy = Pbind.new.patternpairs_(items);
				}
			}./*postln.*/takeThese(_.isNil);
			// pbinds.do { |pb| pb.patternpairs.postln };
			pdef = pdef.add(Pdef((synthDefName ++ (pdef.size)).asSymbol, Ppar(pbinds, inf)));
			pdef.last.play;
			#sampleStart, sampleEnd = nil!2;
		} {
			sampleStart = Main.elapsedTime;
			this.prResetSampling;
		}
	}

	prDurSum { |durs|
		^durs.sum { |num| if (num.isRest) { num.dur } { num }}
	}

	prResetSampling {
		// starttime, absolute
		#onTimes, offTimes = Main.elapsedTime!128!2;
		// the array holding all events for all 128 midi keys
		sampleEvents = ()!128;
	}

	sampleClear { |...indices|
		indices ?? { pdef.do(_.clear) };
		indices.do(pdef[_].clear);
	}
}