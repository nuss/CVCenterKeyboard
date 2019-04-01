CVCenterKeyboard {
	classvar <allKeyboards;
	var <name, <synthDefNames, <controls/*, synthDefName, */;
	// var <outArg, <keyboardArg, <velocArg, <bendArg, <widgetsPrefix, <>srcID;
	var <>bendSpec, <>out, <server;
	var <currentSynthDef, <wdgtNames, <outProxy;
	var <sample = false, <sampleEvents, <pdef;
	var on, off, bend, namesCVs, onTimes, offTimes, sampleStart, sampleEnd;
	var <>debug = false;

	*initClass {
		Spec.add(\midiBend, ControlSpec(0.midicps.neg, 0.midicps, \lin, 0, 0, " hz"));
	}

	*new { |name|
		^super.newCopyArgs(name.asSymbol).init;
	}

	init {
		allKeyboards ?? { allKeyboards = () };
		allKeyboards[name] ?? { allKeyboards.put(name, this) };
		synthDefNames ?? { synthDefNames = [] };
	}

	addSynthDef { |synthDefName, outArg = \out, keyBoardArg = \freq, velocArg = \veloc, bendArg = \bend, widgetsPrefix = \kb, srcID, connectMidi = true|
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

		synthDefNames[synthDefName.asSymbol] ?? {
			synthDefNames = synthDefNames.add(synthDefName.asSymbol);
		};

		this.bendSpec ?? {
			this.bendSpec = \midiBend.asSpec;
		};

		this.prMidiInit(synthDefName, false);
	}

	*newSynthDef { |name, synthDefName, outArg = \out, keyboardArg = \freq, velocArg = \veloc, bendArg = \bend, widgetsPrefix = \kb, srcID, connectMidi = true|
		^super.newCopyArgs(outArg, keyboardArg, velocArg, bendArg, widgetsPrefix, srcID).initSynthDef(name, synthDefName, connectMidi);
	}

	initSynthDef { |synthDefName, connectMidi|
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

		synthDefNames.indexOf(synthDefName) ?? {
			synthDefNames = synthDefNames.add(synthDefName.asSymbol);
		};

		allKeyboards ?? {
			allKeyboards = ();
		};

		allKeyboards[name] ?? {
			allKeyboards.put(name, this);
		};

		this.bendSpec ?? {
			this.bendSpec = \midiBend.asSpec;
		};

		this.prMidiInit(synthDefName, connectMidi);
	}

	// keyboardArg is the arg that will be set through playing the keyboard
	// bendArg will be the arg that's set through the pitch bend wheel
	setUpControls { |synthDefName, prefix, outControl, keyboardControl, velocControl, bendControl, theServer, out=0, deactivateDefaultWidgetActions = true, srcID, tab|
		var testSynth, notesEnv;
		var args = [];

		currentSynthDef = synthDefName.asSymbol;

		synthDefNames.indexOf(name.asSymbol) ?? {
			Error("SynthDef '%' must be added to CVCenterKeyboard instance '%' before it using".format(synthDefName, name)).throw;
		};

		if (theServer.isNil) {
			server = Server.default
		} {
			server = theServer;
		};

		controls ?? {
			controls = ();
		};

		controls[synthDefName] ?? {
			controls.put(synthDefName, ());
		};

		prefix !? {
			controls[synthDefName].prefix = prefix;
		};
		outControl !? {
			controls[synthDefName].outControl = outControl;
		};
		keyboardControl !? {
			controls[synthDefName].keyboardControl = keyboardControl;
		};
		velocControl !? {
			controls[synthDefName].velocControl = velocControl;
		};
		bendControl !? {
			controls[synthDefName].bendControl = bendControl;
		};
		srcID !? {
			controls[synthDefName].srcID = srcID;
		};
		controls[synthDefName].out ?? {
			controls[synthDefName].out = out;
		};

		// prefix !? { widgetsPrefix = prefix };
		// outControl !? { outArg = outControl };
		// keyboardControl !? { keyboardArg = keyboardControl };
		// velocControl !? { velocArg = velocControl };
		// bendControl !? { bendArg = bendControl };
		// srcID !? { this.srcID = srcID };

		// this.out ?? { this.out = out };

		tab ?? { tab = synthDefName };

		server.waitForBoot {
			// SynthDef *should* have an \amp arg, otherwise it will sound for moment
			testSynth = Synth(name.asSymbol);
			// \gate will be set internally
			testSynth.cvcGui(
				prefix: controls[synthDefName].prefix,
				excemptArgs: [
					controls[synthDefName].out,
					controls[synthDefName].keyboardControl,
					controls[synthDefName].velocControl,
					\gate
				], tab: tab, completionFunc: {
				this.prAddWidgetActionsForKeyboard(synthDefName, deactivateDefaultWidgetActions);
			});
			testSynth.release;
		}
	}

	// private
	prMidiInit { |synthDefName, connectMidi|
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

	prAddWidgetActionsForKeyboard { |synthDefName, deactivateDefaultActions|
		var args = SynthDescLib.at(synthDefName).controlDict.keys.asArray;
		var wdgtName, nameString;

		this.prInitCVs(synthDefName, args);

		args.do { |name, i|
			wdgtName = wdgtNames[i];
			CVCenter.cvWidgets[wdgtName] !? {
				if (CVCenter.cvWidgets[wdgtName].class == CVWidget2D) {
					#[lo, hi].do { |slot|
						var setString;
						if (slot == \lo) {
							setString = "[cv.value, CVCenter.at('%').hi]".format(wdgtName);
						} {
							setString = "[CVCenter.at('%').lo, cv.value]".format(wdgtName);
						};
						CVCenter.addActionAt(
							wdgtName, 'keyboard set arg',
							"{ |cv| CVCenter.scv['%'].do { |synth| synth !? { synth.set('%', %) }}; }"
							.format(synthDefName, name, setString), slot);
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

		this.prInitKeyboard(synthDefName);
	}

	reInit { |synthDefName|
		var args;
		synthDefName ?? {
			synthDefName = currentSynthDef;
		};
		args = SynthDescLib.at(synthDefName).controlDict.keys.asArray;
		CVCenter.scv[synthDefName] !? {
			"re-initializing!".postln;
			this.free;
			CVCenter.scv.put(synthDefName, Array.newClear(128));
			this.prInitCVs(synthDefName, args);
			this.prInitKeyboard(synthDefName);
		}
	}

	// private
	prInitCVs { |synthDefName, args|
		var nameString, wdgtName;

		#wdgtNames, namesCVs = []!2;

		args.do { |name|
			nameString = name.asString;
			controls[synthDefName].prefix !? {
				nameString = nameString[0].toUpper ++ nameString[1..nameString.size-1];
			};
			wdgtName = (controls[synthDefName].prefix ++ nameString).asSymbol;
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
	prInitKeyboard { |synthDefName|
		on = MIDIFunc.noteOn({ |veloc, num, chan, src|
			var argsValues = [
				keyboardArg,
				num.midicps,
				velocArg,
				veloc * 0.005,
				outArg,
				this.out
			] ++ namesCVs.deepCollect(2, _.value);
			if (this.debug) { "on[num: %]: %\n\nchan: %, src: %\n".postf(num, argsValues, chan, src) };
			if (srcID.isNil or: { this.srcID.notNil and: this.srcID == src }) {
				CVCenter.scv[synthDefName][num] = Synth(synthDefName, argsValues);
			};
			if (sample) {
				onTimes[num] = Main.elapsedTime;
				argsValues.pairsDo { |k, v|
					sampleEvents[num][k] ?? {
						sampleEvents[num].put(k, [])
					};
					if (v.size > 1) {
						// multichannel-expand arrayed args properly
						sampleEvents[num][k] = sampleEvents[num][k].add([(v)]);
					} {
						sampleEvents[num][k] = sampleEvents[num][k].add(v);
					};
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
				CVCenter.scv[synthDefName][num] = nil;
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
			if (controls[synthDefName].srcID.isNil or: {
				controls[synthDefName].srcID.notNil and: {
					controls[synthDefName].srcID == src
				}
			}) {
				CVCenter.scv[synthDefName].do({ |synth, i|
					synth.set(controls[synthDefName].bendControl, (i + bendSpec.map(bendVal / 16383)).midicps)
				})
			}
		});
	}

	addOutProxy { |synthDefName, numChannels=2, useNdef=false|
		var proxyName;
		synthDefName ?? {
			synthDefName = currentSynthDef;
		};
		proxyName = (synthDefName ++ "Out").asSymbol;
		controls[synthDefName].out_(Bus.audio(server, numChannels));
		if (useNdef.not) {
			outProxy = NodeProxy.audio(server, numChannels);
		} {
			Ndef(proxyName).mold(numChannels, \audio, \elastic);
			outProxy = Ndef(proxyName);
		};
		outProxy.source = {
			In.ar(controls[synthDefName].out, numChannels)
		};
		outProxy.play;
	}

	removeOutProxy { |synthDefName, out=0|
		synthDefName ?? {
			synthDefName = currentSynthDef;
		};
		controls[synthDefName].out_(out);
		outProxy.clear;
	}

	free { |synthDefName|
		synthDefName ?? {
			synthDefName = currentSynthDef;
		};
		on !? { on.free };
		off !? { off.free };
		bend !? { bend.free };
		CVCenter.scv[synthDefName].do(_.release);
		CVCenter.scv.removeAt(synthDefName);
	}

	// start/stop sampling
	activateSampling { |onOff, synthDefName|
		var pbinds, items, pbproxy, last;
		synthDefName ?? {
			synthDefName = currentSynthDef;
		};
		sample = onOff;
		if (sample == false) {
			sampleEnd = Main.elapsedTime;
			pdef ?? { pdef = [] };
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
					items = [\instrument, synthDefName, keyboardArg, num.midicps]
					++ slot.collect(Pseq(_, inf)).asPairs;
					/*++ slot.collect { |v, k|
						if (CVCenter.at((k.asString[0].toUpper ++ k.asString[1..]).asSymbol).notNil) {
							if (v.size < 2) {
							Pseq(CVCenter.at((k.asString[0].toUpper ++ k.asString[1..]).asSymbol).value_(v), inf)}
						}
					};*/
					pbproxy = Pbind.new.patternpairs_(items);
				}
			}./*postln.*/takeThese(_.isNil);
			if (pbinds.isEmpty.not) {
				// pbinds.do { |pb| pb.patternpairs.postln };
				pdef = pdef.add(Pdef((synthDefName ++ (pdef.size)).asSymbol, Ppar(pbinds, inf)));
				pdef.last.play;
				#sampleStart, sampleEnd = nil!2;
				"\nsampling keyboard events finished, should start playing now\n".inform;
			} {
				"\nnothing recorded, please try again\n".inform;
			}
		} {
			sampleStart = Main.elapsedTime;
			this.prResetSampling;
			"\nsampling keyboard events started\n".inform;
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

	clearSamples { |...indices|
		if (indices.isEmpty) {
			pdef.do { |p| p.clear }
		} {
			indices.do { |i| pdef[i].clear };
		}
	}
}
