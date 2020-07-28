CVCenterKeyboard {
	classvar <all;
	var <keyboardDefName, <synthDefNames, synthParams;
	var <>bendSpec, <>out, <server, group;
	var <currentSynthDef, <wdgtNames, <outProxy;
	var sampling = false, sampleEvents, <pdef;
	var on, off, bend, namesCVs, onTimes, offTimes, sampleStart, sampleEnd;
	var <select;
	var <>debug = false;

	*initClass {
		StartUp.add {
			Spec.add(\midiBend, ControlSpec(0.midicps.neg, 0.midicps, \lin, 0, 0, " hz"));
			all ?? { all = () };
		};
	}

	*new { |keyboardDefName=\keyboard|
		^super.newCopyArgs(keyboardDefName.asSymbol).init;
	}

	init {
		all[keyboardDefName] ?? { all.put(keyboardDefName, this) };
		#on, off, bend = ()!3;
		synthDefNames ?? { synthDefNames = List() };
	}

	addSynthDef { |synthDefName, connectMidi = false|
		this.prInitSynthDef(synthDefName.asSymbol, connectMidi);
	}

	*at { |keyboardDefName|
		^all[keyboardDefName]
	}

	*newSynthDef { |synthDefName, keyboardDefName=\keyboard, connectMidi = true|
		var instance = this.new(keyboardDefName.asSymbol);
		synthDefName = synthDefName.asSymbol;
		instance.addSynthDef(synthDefName, connectMidi);
		^instance;
	}

	prInitSynthDef { |synthDefName, connectMidi|
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

		synthDefNames ?? { synthDefNames = List() };
		synthDefNames.indexOf(synthDefName) ?? {
			// dependency declared in CVCenterKeyboardSelect:-init
			// automatically update the select's items
			synthDefNames.add(synthDefName).changed(\value);
		};


		this.bendSpec ?? {
			this.bendSpec = \midiBend.asSpec;
		};

		this.prMidiInit(synthDefName, connectMidi);
	}

	// keyboardArg is the arg that will be set through playing the keyboard
	// bendArg will be the arg that's set through the pitch bend wheel
	setUpControls { |synthDefName, prefix, pitchControl=\freq, velocControl=\veloc, bendControl=\bend, outControl=\out, includeInCVCenter=#[], theServer, outbus=0, deactivateDefaultWidgetActions = true, srcID, tab|
		var testSynth, notesEnv, excemptArgs = [];
		var args = [];

		pitchControl = pitchControl.asSymbol;
		velocControl = velocControl.asSymbol;
		bendControl = bendControl.asSymbol;
		outControl = outControl.asSymbol;
		includeInCVCenter !? {
			includeInCVCenter = includeInCVCenter.collect { |name| name.asSymbol }
		};

		synthDefName = synthDefName.asSymbol;
		currentSynthDef = synthDefName;

		synthDefNames.indexOf(synthDefName) ?? {
			Error("SynthDef '%' must be added to CVCenterKeyboard instance '%'
before using it".format(synthDefName, keyboardDefName)).throw;
		};

		if (theServer.isNil) {
			server = Server.default
		} {
			server = theServer;
		};

		synthParams ?? {
			synthParams = ();
		};

		synthParams[synthDefName] ?? {
			synthParams.put(synthDefName, ());
		};

		prefix !? {
			synthParams[synthDefName].prefix = prefix;
		};
		outControl !? {
			synthParams[synthDefName].outControl = outControl;
			includeInCVCenter.includes(outControl).not.if {
				excemptArgs = excemptArgs.add(outControl)
			}
		};
		pitchControl !? {
			synthParams[synthDefName].pitchControl = pitchControl;
			includeInCVCenter.includes(pitchControl).not.if {
				excemptArgs = excemptArgs.add(pitchControl)
			}
		};
		velocControl !? {
			synthParams[synthDefName].velocControl = velocControl;
				includeInCVCenter.includes(velocControl).not.if {
				excemptArgs = excemptArgs.add(velocControl)
			}
		};
		bendControl !? {
			synthParams[synthDefName].bendControl = bendControl;
			includeInCVCenter.includes(bendControl).not.if {
				excemptArgs = excemptArgs.add(bendControl)
			}
		};
		srcID !? {
			synthParams[synthDefName].srcID = srcID;
		};
		outbus !? { this.out_(outbus) };

		excemptArgs = excemptArgs.add(\gate);

		tab ?? { tab = synthDefName };

		server.waitForBoot {
			// SynthDef *should* have an \amp arg, otherwise it will sound for moment
			testSynth = Synth(synthDefName);
			// \gate will be set internally
			testSynth.cvcGui(
				prefix: synthParams[synthDefName].prefix,
				excemptArgs: excemptArgs,
				tab: tab,
				completionFunc: {
					this.prAddWidgetActionsForKeyboard(synthDefName, deactivateDefaultWidgetActions);
				}
			);
			testSynth.release;
		}
	}

	// private
	prMidiInit { |synthDefName, connectMidi|
		CVCenter.scv[keyboardDefName] ?? { CVCenter.scv.put(keyboardDefName, ()) };
		if (CVCenter.scv[keyboardDefName][synthDefName].isNil) {
			CVCenter.scv[keyboardDefName].put(synthDefName, Array.newClear(128));
			if (MIDIClient.initialized.not) {
				MIDIClient.init;
				// doesn't seem to work properly on Ubuntustudio 16
				// possibly has to be done manually in QJackQtl...
				if (connectMidi) {
					try { MIDIIn.connectAll } { |error|
						error.postln;
						"MIDIIn.connectAll failed. Please establish the necessary connections manually".warn;
					}
				}
			}
		} {
			"A keyboard named '%' has already been initialized".format(keyboardDefName).warn;
		}

	}

	prAddWidgetActionsForKeyboard { |synthDefName, deactivateDefaultActions|
		var args = SynthDescLib.at(synthDefName).controlDict.keys.asArray;
		var wdgtName, nameString;

		this.prInitCVs(synthDefName, args);

		args.do { |argName, i|
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
							"{ |cv| CVCenter.scv['%']['%'].do { |synth| synth !? { synth.set('%', %) }}; }"
							.format(keyboardDefName, synthDefName, argName, setString), slot);
						CVCenter.activateActionAt(wdgtName, \default, deactivateDefaultActions.not, slot);
					}
				} {
					CVCenter.addActionAt(wdgtName, 'keyboard set arg',
						"{ |cv| CVCenter.scv['%']['%'].do { |synth| synth !? { synth.set('%', cv.value) }}; }"
						.format(keyboardDefName, synthDefName, argName));
					CVCenter.activateActionAt(wdgtName, \default, deactivateDefaultActions.not);
				}
			}
		};
	}

	switchSynthDef { |synthDefName|
		synthDefName = synthDefName.asSymbol;
		currentSynthDef = synthDefName;
		this.free;
		this.freeHangingNodes; // just in case...
		CVCenter.scv.put(keyboardDefName, ());
		CVCenter.scv[keyboardDefName].put(synthDefName, nil!128);
		this.prInitKeyboard(synthDefName);
	}

	reInit { |synthDefName|
		var args;
		if (synthDefName.notNil) {
			synthDefName = synthDefName.asSymbol;
		} {
			synthDefName = currentSynthDef;
		};
		args = SynthDescLib.at(synthDefName).controlDict.keys.asArray;
		CVCenter.scv[keyboardDefName] ?? { CVCenter.scv.put(keyboardDefName, ()) };
		CVCenter.scv[keyboardDefName][synthDefName] !? {
			"re-initializing: '%'\n".postf(synthDefName);
			this.free;
			CVCenter.scv.put(keyboardDefName, ());
			CVCenter.scv[keyboardDefName].put(synthDefName, Array.newClear(128));
			this.prInitCVs(synthDefName, args);
			this.prInitKeyboard(synthDefName);
		}
	}

	createSelect { |tab|
		select = CVCenterKeyboardSelect(this, tab);
		^select.front;
	}

	// private
	prInitCVs { |synthDefName, args|
		var nameString, wdgtName;

		#wdgtNames, namesCVs = []!2;

		args.do { |argName|
			nameString = argName.asString;
			synthParams[synthDefName].prefix !? {
				nameString = nameString[0].toUpper ++ nameString[1..nameString.size-1];
			};
			wdgtName = (synthParams[synthDefName].prefix ++ nameString).asSymbol;
			CVCenter.cvWidgets[wdgtName] !? {
				if (CVCenter.cvWidgets[wdgtName].class == CVWidget2D) {
					if (namesCVs.includes(argName).not) {
						namesCVs = namesCVs.add(argName).add(CVCenter.at(wdgtName).asArray);
					}
				} {
					if (namesCVs.includes(argName).not) {
						namesCVs = namesCVs.add(argName).add(CVCenter.at(wdgtName));
					}
				}
			};
			wdgtNames = wdgtNames.add(wdgtName);
		};
	}

	// private
	prInitKeyboard { |synthDefName|
		group ?? {
			group = ParGroup.new;
		};
		on[keyboardDefName] = MIDIFunc.noteOn({ |veloc, num, chan, src|
			var kbArgs = [
				synthParams[synthDefName].pitchControl,
				num.midicps,
				synthParams[synthDefName].velocControl,
				veloc * 0.005,
				synthParams[synthDefName].outControl,
				// FIXME: this should probably be this.out, shouldn't it?
				// synthParams[synthDefName].out
				this.out;
			];
			// sort out keyboard-controlled args, they should only get set once.
			// this may happen if an existing CVCenter setup gets loaded after 'setUpControls'
			var pairs = namesCVs.clump(2).select { |pair| kbArgs.includes(pair[0]).not }.flatten(1);
			var argsValues = kbArgs ++ pairs.deepCollect(2, _.value);
			// "kbArgs: %\n\npairs: %\n\nargsValues: %\n".postf(kbArgs, pairs, argsValues);
			if (this.debug) { "on['%']['%'][num: %]: %\n\nchan: %, src: %\n".postf(keyboardDefName, synthDefName, num, argsValues, chan, src) };
			if (synthParams[synthDefName].srcID.isNil or: {
				synthParams[synthDefName].srcID.notNil and: {
					synthParams[synthDefName].srcID == src
				}
			}) {
				CVCenter.scv[keyboardDefName][synthDefName][num] = Synth(synthDefName, argsValues, group);
			};
			if (sampling) {
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

		off[keyboardDefName] = MIDIFunc.noteOff({ |veloc, num, chan, src|
			if (this.debug) { "off['%']['%'][num: %]\n\n".postf(keyboardDefName, synthDefName, num) };
			if (synthParams[synthDefName].srcID.isNil or: {
				synthParams[synthDefName].srcID.notNil and: {
					synthParams[synthDefName].srcID == src
				}
			}) {
				CVCenter.scv[keyboardDefName][synthDefName][num].release;
				CVCenter.scv[keyboardDefName][synthDefName][num] = nil;
			};
			if (sampling) {
				offTimes[num] = Main.elapsedTime;
				sampleEvents[num].dur ?? {
					sampleEvents[num].put(\dur, []);
				};
				sampleEvents[num].dur = sampleEvents[num].dur.add(offTimes[num] - onTimes[num]);
			}
		});

		bend[keyboardDefName] = MIDIFunc.bend({ |bendVal, chan, src|
			if (this.debug) { "bend['%']['%']: %\n".postf(keyboardDefName, synthDefName, bendVal) };
			if (synthParams[synthDefName].srcID.isNil or: {
				synthParams[synthDefName].srcID.notNil and: {
					synthParams[synthDefName].srcID == src
				}
			}) {
				CVCenter.scv[keyboardDefName][synthDefName].do({ |synth, i|
					synth.set(synthParams[synthDefName].bendControl, (i + bendSpec.map(bendVal / 16383)).midicps)
				})
			}
		});
	}

	addOutProxy { |synthDefName, numChannels=2, useNdef=false, outbus, play=true|
		var proxyName;
		if (synthDefName.notNil) {
			synthDefName = synthDefName.asSymbol;
		} {
			synthDefName = currentSynthDef;
		};
		proxyName = (keyboardDefName ++ "Out").asSymbol;
		if (useNdef.not) {
			outProxy = NodeProxy.audio(server, numChannels);
		} {
			Ndef(proxyName).mold(numChannels, \audio, \elastic);
			outProxy = Ndef(proxyName);
		};
		outProxy.source = {
			// In.ar(synthParams[synthDefName].out, numChannels)
			In.ar(this.out, numChannels)
		};
		"out: %\n".postf(out);
		if (play) {
			outProxy.play(outbus ? this.out);
		}
	}

	removeOutProxy { |synthDefName, outbus|
		if (synthDefName.notNil) {
			synthDefName = synthDefName.asSymbol;
		} {
			synthDefName = currentSynthDef;
		};
		outbus !? { this.out_(outbus) };
		outProxy.clear;
	}

	free { |synthDefName|
		if (synthDefName.notNil) {
			synthDefName = synthDefName.asSymbol;
		} {
			synthDefName = currentSynthDef;
		};
		on !? { on[keyboardDefName].free };
		off !? { off[keyboardDefName].free };
		bend !? { bend[keyboardDefName].free };
		CVCenter.scv[keyboardDefName][synthDefName].do(_.release);
		CVCenter.scv[keyboardDefName].removeAt(synthDefName);
		CVCenter.scv.removeAt(keyboardDefName);
	}

	// start/stop sampling
	activateSampling { |onOff = true, synthDefName|
		var pbinds, items, pbproxy, last;
		if (synthDefName.notNil) {
			synthDefName = synthDefName.asSymbol;
		} {
			synthDefName = currentSynthDef;
		};
		sampling = onOff;
		if (sampling == false) {
			sampleEnd = Main.elapsedTime;
			pdef ?? { pdef = [] };
			sampleEvents.do { |e, num|
				// add last event - not considered within noteOn, notOff
				e.dur !? {
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
					items = [\instrument, synthDefName, synthParams[synthDefName].pitchControl, num.midicps]
					++ slot.collect(Pseq(_, inf)).asPairs;
					/*++ slot.collect { |v, k|
						if (CVCenter.at((k.asString[0].toUpper ++ k.asString[1..]).asSymbol).notNil) {
							if (v.size < 2) {
							Pseq(CVCenter.at((k.asString[0].toUpper ++ k.asString[1..]).asSymbol).value_(v), inf)}
						}
					};*/
					pbproxy = Pbind.new.patternpairs_(items);
				}
			}.takeThese(_.isNil);
			if (pbinds.isEmpty.not) {
				// pbinds.do { |pb| pb.patternpairs.postln };
				pdef = pdef.add(Pdef((synthDefName ++ "-" ++ (pdef.size)).asSymbol, Ppar(pbinds, inf)));
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
		};
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

	freeHangingNodes {
		group.deepFree;
	}
}
