CVCenterKeyboard {
	classvar <all;
	var <keyboardDefName, <synthDefNames, <synthParams, wdgtName, <touchOSC;
	var <>bendSpec, <>out, <server, <group;
	var <currentSynthDef, wdgtNames, <outProxy;
	var <recorder, sampling = false, sampleEvents;
	var <on, <off, <bend, <namesCVs, onTimes, offTimes, sampleStart, sampleEnd;
	var <onFuncs, <offFuncs, <bendFuncs; // 3 Events noteOn/noteOff/bend funcs for each SynthDef. Must be added with SynthDef
	var <>debug = false;
	var <mappedBusses;
	// sequencer support;
	var <pairs; // pairs of controls / CVs
	var <valuePairs; // pairs of args / cv.values

	*initClass {
		StartUp.add {
			Spec.add(\midiBend, ControlSpec(0.midicps.neg, 0.midicps, \lin, 0, 0, " hz"));
			all = ();
		};
	}

	*new { |keyboardDefName=\keyboard, srcID, chan, addRecorder=true, touchOSCAddr|
		all[keyboardDefName.asSymbol] !? {
			"A CVCenterKeyboard instance at '%' already exists. Please choose a different name!".format(keyboardDefName).error;
			^nil;
		}
		^super.newCopyArgs(keyboardDefName.asSymbol).init(srcID, chan, addRecorder, touchOSCAddr);
	}

	*newSynthDef { |synthDefName, keyboardDefName=\keyboard, srcID, chan, addRecorder=true, touchOSCAddr|
		var instance = this.new(keyboardDefName.asSymbol, srcID, chan, addRecorder, touchOSCAddr);
		synthDefName = synthDefName.asSymbol;
		if (SynthDescLib.at(synthDefName).notNil) {
			instance.synthDefNames.add(synthDefName);
			instance.addSynthDef(synthDefName);
			^instance;
		} {
			"No SynthDef found for the given synthDefName".error;
		}
	}

	init { |srcID, chan, addRecorder, oscAddr|
		\CVCenter.asClass ?? {
			"CVCenterKeyboard depends on CVCenter to be installed. Please install CVCenter before creating a new CVCenterKeyboard instance!".error;
			^nil;
		};
		all.put(keyboardDefName, this);
		group = ParGroup.new;
		synthDefNames = List[];
		if (MIDIClient.initialized.not) {
			MIDIClient.init;
			// doesn't seem to work properly on Ubuntustudio 16
			// possibly has to be done manually in QJackQtl...
			try { MIDIIn.connectAll } { |error|
				error.postln;
				"MIDIIn.connectAll failed. Please establish the necessary connections manually".warn;
			}
		};
		if (oscAddr.notNil and: { oscAddr.class == NetAddr }) {
			touchOSC = CVCenterKeyboardTouchOSC(keyboardDefName, oscAddr)
		};
		on = MIDIFunc.noteOn({ |veloc, num, chan, src|
			if (this.debug) { "MIDIFunc.noteOn initialized properly".postln }
		}, chan, srcID);
		off = MIDIFunc.noteOff({ |veloc, num, chan, src|
			if (this.debug) { "MIDIFunc.noteOff initialized properly".postln }
		}, chan, srcID);
		bend = MIDIFunc.bend({ |bendVal, chan, src|
			if (this.debug) { "MIDIFunc.bend initialized properly".postln }
		}, chan, srcID);
		#onFuncs, offFuncs, bendFuncs, mappedBusses = ()!4;
		if (addRecorder and: { recorder.isNil }) {
			recorder = CVCenterKeyboardRecorder(this);
		};
		CVCenter.use(keyboardDefName, tab: \default, svItems: ['select Synth...'])
	}

	addSynthDef { |synthDefName|
		synthDefName = synthDefName.asSymbol;
		if (SynthDescLib.at(synthDefName).notNil) {
			if (synthDefNames.includes(synthDefName).not) {
				synthDefNames.add(synthDefName)
			};
			this.prInitSynthDef(synthDefName);
		} {
			"[CVCenterKeyboard] No SynthDef found for the given synthDefName".error;
		}
	}

	removeSynthDef { |synthDefName|
		synthDefName = synthDefName.asSymbol;
		CVCenter.at(keyboardDefName) !? {
			CVCenter.at(keyboardDefName).items.remove(synthDefName);
			CVCenter.at(keyboardDefName).items_(CVCenter.at(keyboardDefName).items);
		};
		this.clear(synthDefName);
		[onFuncs, offFuncs].do { |f| f[synthDefName] = nil };
		// CVCenter.scv[keyboardDefName].removeAt(synthDefName);
	}

	*at { |keyboardDefName|
		^all[keyboardDefName.asSymbol]
	}

	prInitSynthDef { |synthDefName|
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

		if (CVCenter.at(keyboardDefName).notNil and: {
			CVCenter.at(keyboardDefName).items.includes(synthDefName).not
		}) {
			// dependency declared in CVCenterKeyboardSelect:-init
			// automatically update the select's items
			CVCenter.at(keyboardDefName) !? {
				CVCenter.at(keyboardDefName).items_(CVCenter.at(keyboardDefName).items ++ synthDefName)
			}
		};


		this.bendSpec ?? {
			this.bendSpec = \midiBend.asSpec;
		};

		this.prEnvInit(synthDefName);
	}

	// keyboardArg is the arg that will be set through playing the keyboard
	// bendArg will be the arg that's set through the pitch bend wheel
	setUpControls { |synthDefName, prefix, pitchControl=\freq, velocControl=\veloc, bendControl=\bend, outControl=\out, includeInCVCenter=#[], theServer, outbus=0, deactivateDefaultWidgetActions = true, tab, setSynthDef=false|
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

		SynthDescLib.at(synthDefName) ?? {
			Error("No SynthDef '%' found in the SynthDescLib".format(synthDefName)).throw;
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
			if (setSynthDef) {
				this.setSynthDef(synthDefName)
			}
		}
	}

	// private
	prEnvInit { |synthDefName|
		CVCenter.scv[keyboardDefName] ?? { CVCenter.scv.put(keyboardDefName, ()) };
		if (CVCenter.scv[keyboardDefName][synthDefName].isNil) {
			CVCenter.scv[keyboardDefName].put(synthDefName, Array.newClear(128));
		} {
			"Synth '%' already set for CVCenterKeyboard '%'".format(synthDefName, keyboardDefName).warn;
		}
	}

	prAddWidgetActionsForKeyboard { |synthDefName, deactivateDefaultActions|
		var args = SynthDescLib.at(synthDefName).controlDict.keys.asArray;
		var wdgtName, argName, cv, nameString;

		this.prInitCVs(synthDefName, args);

		forBy (0, namesCVs[synthDefName].size-1, 3) { |i|
			wdgtName = namesCVs[synthDefName][i];
			argName = namesCVs[synthDefName][i + 1];
			cv = namesCVs[synthDefName][i + 2];

			CVCenter.cvWidgets[wdgtName] !? {
				if (CVCenter.cvWidgets[wdgtName].class == CVWidget2D) {
					#[lo, hi].do { |slot|
						var setString;
						if (slot === \lo) {
							setString = "[cv.value, CVCenter.at('%').hi.value]".format(wdgtName);
						} {
							setString = "[CVCenter.at('%').lo.value, cv.value]".format(wdgtName);
						};
						CVCenter.addActionAt(
							wdgtName, 'keyboard set arg',
							"{ |cv| CVCenterKeyboard.all['%'].group.set('%', %) }"
							.format(keyboardDefName, argName, setString), slot);
						CVCenter.activateActionAt(wdgtName, \default, deactivateDefaultActions.not, slot);
					}
				} {
					CVCenter.addActionAt(wdgtName, 'keyboard set arg',
						"{ |cv| CVCenterKeyboard.all['%'].group.set('%', cv.value) }"
						.format(keyboardDefName, argName));
					CVCenter.activateActionAt(wdgtName, \default, deactivateDefaultActions.not);
				}
			}
		};
	}

	setSynthDef { |synthDefName|
		// this.freeHangingNodes; // just in case...
		this.clear;
		group.free;
		synthDefName = synthDefName.asSymbol;
		currentSynthDef = synthDefName;
		this.prEnvInit(synthDefName);
		this.prInitKeyboard(synthDefName);
		this.on.add(onFuncs[synthDefName]);
		this.off.add(offFuncs[synthDefName]);
		this.bend.add(bendFuncs[synthDefName]);
	}

	record { |onOff|
		if (recorder.notNil) {
			recorder.record(onOff)
		} {
			"[CVCenterKeyboard] No CVCenterKeyboardRecorder instance for the given CVCenterKeyboard instance '%' exists! Create one by calling 'addRecorder' on the CVCenterKeyboard instance.".format(keyboardDefName).error
		}
	}

	// createSelect { |tab|
	// 	select = CVCenterKeyboardSelect(this, tab);
	// 	^select.front;
	// }

	// private
	prInitCVs { |synthDefName, args|
		var nameString, wdgtName;

		// wdgtNames ?? { wdgtNames = () };
		namesCVs ?? { namesCVs = () };
		namesCVs.put(synthDefName, List[]);

		args.do { |argName|
			nameString = argName.asString;
			synthParams[synthDefName].prefix !? {
				nameString = nameString[0].toUpper ++ nameString[1..nameString.size-1];
			};
			wdgtName = (synthParams[synthDefName].prefix ++ nameString).asSymbol;
			CVCenter.cvWidgets[wdgtName] !? {
				namesCVs[synthDefName].add(wdgtName);
				if (namesCVs[synthDefName].includes(argName).not) {
					if (CVCenter.cvWidgets[wdgtName].class == CVWidget2D) {
						namesCVs[synthDefName].add(argName).add(CVCenter.at(wdgtName).asArray);
					} {
						namesCVs[synthDefName].add(argName).add(CVCenter.at(wdgtName));
					}
				}
			}
		}
	}

	// private
	prInitKeyboard { |synthDefName|
		group = ParGroup.new;

		onFuncs.put(synthDefName, { |veloc, num, chan, src|
			var argsValues, wdgtsExcluded;
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

			// look up mappedBusses for Ndef to be placed instead of value
			// to be tested...
			wdgtsExcluded = namesCVs[synthDefName].select { |it, i| i % 3 > 0 };
			pairs = wdgtsExcluded.clump(2).select {
				|pair| kbArgs.includes(pair[0]).not
			}.collect { |pair|
				if (mappedBusses[pair[0]].notNil) {
					[pair[0], mappedBusses[pair[0]]]
				} {
					[pair[0], pair[1]]
				}
			}.flatten(1);
			valuePairs = pairs.deepCollect(2, _.value);
			argsValues = kbArgs ++ valuePairs;
			// "%: argsValues: %".format(argsValues).postln;
			if (this.debug) {
				"\non['%']\n\tsynthDefName: '%' \n\tnum: % \n\tchan: % \n\tsrc: % \n\targsValues: %".format(
					keyboardDefName, synthDefName, num, chan, src, argsValues
				).postln
			};
			CVCenter.scv[keyboardDefName][synthDefName][num] = Synth(synthDefName, argsValues, group);
		});

		offFuncs.put(synthDefName, { |veloc, num, chan, src|
			if (this.debug) {
				"\noff['%']\n\tsynthDefName: '%' \n\tnum: %,\n\tchan: %, \n\tsrc: %".format(keyboardDefName, synthDefName, num, chan, src).postln
			};
			CVCenter.scv[keyboardDefName][synthDefName][num].release;
			CVCenter.scv[keyboardDefName][synthDefName][num] = nil;
		});

		bendFuncs.put(synthDefName, { |bendVal, chan, src|
			if (this.debug) {
				"\nbend['%']['%']: %\n\tbendVal: %\n\tchan: % \n\tsrc: %".postf(keyboardDefName, synthDefName, bendVal, chan, src).postln
			};
			CVCenter.scv[keyboardDefName][synthDefName].do({ |synth, i|
				synth.set(synthParams[synthDefName].bendControl, (i + bendSpec.map(bendVal / 16383)).midicps)
			})
		});
	}

	addOutProxy { |numChannels=2, useNdef=false, transbus, outbus|
		var proxyName;
		proxyName = (keyboardDefName ++ "Out").asSymbol;
		if (useNdef.not) {
			outProxy = NodeProxy.audio(server, numChannels);
		} {
			Ndef(proxyName).mold(numChannels, \audio, \elastic);
			outProxy = Ndef(proxyName);
		};
		outbus ?? {
			outbus = this.out
		};
		transbus !? {
			this.out_(transbus)
		};
		outProxy.source = {
			In.ar(this.out, numChannels)
		};
		outProxy.play(outbus);
	}

	removeOutProxy { |outbus|
		outbus !? { this.out_(outbus) };
		outProxy.clear;
		outProxy = nil;
	}

	clear { |synthDefName|
		if (synthDefName.notNil) {
			synthDefName = synthDefName.asSymbol;
		} {
			synthDefName = currentSynthDef;
		};
		onFuncs[synthDefName] !? { on.remove(onFuncs[synthDefName]) };
		offFuncs[synthDefName] !? { off.remove(offFuncs[synthDefName]) };
		bendFuncs[synthDefName] !? { bend.remove(bendFuncs[synthDefName]) };
		CVCenter.scv[keyboardDefName][synthDefName].do(_.release);
		CVCenter.scv[keyboardDefName].removeAt(synthDefName);
		CVCenter.scv.removeAt(keyboardDefName);
	}

	free {
		onFuncs.do(on.remove(_));
		offFuncs.do(off.remove(_));
		bendFuncs.do(bend.remove(_));
		on !? { on.free };
		off !? { off.free };
		bend !? { bend.free };
		CVCenter.scv[keyboardDefName][currentSynthDef].do(_.release);
		CVCenter.scv.removeAt(keyboardDefName);
		all[keyboardDefName] = nil;
	}

	// add the sequencer, fed through sampling keyboard strokes
	addRecorder {
		if (recorder.isNil) {
			recorder = CVCenterKeyboardRecorder(this);
		} {
			"[CVCenterKeyboard] The given keyboard '%' has already a recorder assigned.".format(keyboardDefName).error;
		}
	}

	addTouchOSC { |addr|
		if (addr.class != NetAddr) {
			Error("The given argument doesn't appear to be a valid NetAddr").throw;
		} {
			touchOSC = CVCenterKeyboardTouchOSC(keyboardDefName, addr);
		}
	}

	freeHangingNodes {
		group.deepFree;
	}

	// control bus mapping
	mapBus { |ctrlname, bus|
		var numBusses = bus.numChannels;
		ctrlname ?? {
			Error("A control name must be given as first argument in 'mapNdef'!").throw;
		};
		mappedBusses.put(ctrlname.asSymbol, numBusses.collect { |i| bus.subBus(i).asMap });
	}

	unmapBus { |ctrlname|
		mappedBusses[ctrlname.asSymbol] = nil;
	}
}
