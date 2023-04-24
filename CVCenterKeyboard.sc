CVCenterKeyboard {
	classvar <all;
	var <keyboardDefName, <synthDefNames, <synthParams, <wdgtName;
	var <>bendSpec, <>out, <server, <group;
	var <currentSynthDef, <wdgtNames, <outProxy;
	var <sampler, sampling = false, sampleEvents, <pdef, cSample = 1;
	var <sampleGroups;
	var <on, <off, <bend, <namesCVs, onTimes, offTimes, sampleStart, sampleEnd;
	var <onFunc, <offFunc, <bendFunc; // 2 Events on/off funcs for each SynthDef. Must be added with SynthDef
	var <select;
	var <>debug = false;
	var <mappedBusses;
	// sequencer support;
	var <pairs; // pairs of controls / CVs
	var <valuePairs; // pairs of args / cv.values

	*initClass {
		StartUp.add {
			Spec.add(\midiBend, ControlSpec(0.midicps.neg, 0.midicps, \lin, 0, 0, " hz"));
			all ?? { all = () };
		};
	}

	*new { |keyboardDefName=\keyboard, addSampler=true, addSelect=false|
		^super.newCopyArgs(keyboardDefName.asSymbol).init(addSampler, addSelect);
	}

	init { |addSampler, addSelect|
		all.put(keyboardDefName, this);
		group = ParGroup.new;
		synthDefNames = List();
		if (MIDIClient.initialized.not) {
			MIDIClient.init;
			// doesn't seem to work properly on Ubuntustudio 16
			// possibly has to be done manually in QJackQtl...
			try { MIDIIn.connectAll } { |error|
				error.postln;
				"MIDIIn.connectAll failed. Please establish the necessary connections manually".warn;
			}
		};
		on = MIDIFunc.noteOn({ |veloc, num, chan, src| });
		off = MIDIFunc.noteOff({ |veloc, num, chan, src| });
		bend = MIDIFunc.bend({ |bendVal, chan, src| });
		#onFunc, offFunc, bendFunc, mappedBusses = ()!4;
		if (addSampler and: {
			sampler.isNil and: {
				\CVCenterKeyboardSampler.asClass.notNil
			}
		}) {
			sampler = CVCenterKeyboardSampler(this)
		};
		CVCenter.use(keyboardDefName, tab: \default, svItems: ['select Synth...'])
		// if (addSelect) {...}
	}

	addSynthDef { |synthDefName, connectMidi = false|
		this.prInitSynthDef(synthDefName.asSymbol, connectMidi);
	}

	removeSynthDef { |synthDefName|
		CVCenter.at(keyboardDefName) !? {
			CVCenter.at(keyboardDefName).items.remove(synthDefName);
			CVCenter.at(keyboardDefName).items_(CVCenter.at(keyboardDefName).items);
		};
		[onFunc, offFunc].do { |f| f[synthDefName] = nil };
		CVCenter.scv[keyboardDefName].removeAt(synthDefName);
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
			this.prInitKeyboard(synthDefName);
		}
	}

	// private
	prEnvInit { |synthDefName|
		CVCenter.scv[keyboardDefName] ?? { CVCenter.scv.put(keyboardDefName, ()) };
		if (CVCenter.scv[keyboardDefName][synthDefName].isNil) {
			CVCenter.scv[keyboardDefName].put(synthDefName, Array.newClear(128));
		} {
			"A keyboard named '%' has already been initialized".format(keyboardDefName).warn;
		}
	}

	prAddWidgetActionsForKeyboard { |synthDefName, deactivateDefaultActions|
		var args = SynthDescLib.at(synthDefName).controlDict.keys.asArray;
		var wdgtName, argName, cv, nameString;

		this.prInitCVs(synthDefName, args);

		forBy(0, namesCVs[synthDefName].size-1, 3) { |i|
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

	switchSynthDef { |synthDefName|
		synthDefName = synthDefName.asSymbol;
		currentSynthDef = synthDefName;
		this.free;
		this.freeHangingNodes; // just in case...
		CVCenter.scv.put(keyboardDefName, ());
		CVCenter.scv[keyboardDefName].put(synthDefName, nil!128);
		// this.updateKeyboard(synthDefName);
	}

	sample { |onOff|
		if (sampler.notNil) {
			sampler.sample(onOff)
		} {
			"No CVCenterKeyboardSampler for the given CVCenterKeyboard exists! Create one by calling CVCenterKeyboardSampler(<myKeyboard>).".error
		}
	}

	createSelect { |tab|
		select = CVCenterKeyboardSelect(this, tab);
		^select.front;
	}

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

		onFunc.put(synthDefName, { |veloc, num, chan, src|
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
			if (this.debug) {
				"\non['%']\n\tsynthDefName: '%' \n\tnum: % \n\tchan: % \n\tsrc: % \n\targsValues: %".format(
					keyboardDefName, synthDefName, num, chan, src, argsValues
				).postln
			};
			if (synthParams[synthDefName].srcID.isNil or: {
				synthParams[synthDefName].srcID.notNil and: {
					synthParams[synthDefName].srcID == src
				}
			}) {
				CVCenter.scv[keyboardDefName][synthDefName][num] = Synth(synthDefName, argsValues, group);
			}
		});

		offFunc.put(synthDefName, { |veloc, num, chan, src|
			if (this.debug) {
				"\noff['%']\n\tsynthDefName: '%' \n\tnum: %".format(keyboardDefName, synthDefName, num)
			}.postln;
			if (synthParams[synthDefName].srcID.isNil or: {
				synthParams[synthDefName].srcID.notNil and: {
					synthParams[synthDefName].srcID == src
				}
			}) {
				CVCenter.scv[keyboardDefName][synthDefName][num].release;
				CVCenter.scv[keyboardDefName][synthDefName][num] = nil;
			}
		});

		bendFunc.put(synthDefName, { |bendVal, chan, src|
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
		this.on.add(onFunc[synthDefName]);
		this.off.add(offFunc[synthDefName]);
		this.bend.add(bendFunc[synthDefName]);
	}

	addOutProxy { |synthDefName, numChannels=2, useNdef=false, transbus, outbus, play=true|
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
		outbus ?? {
			outbus = this.out
		};
		transbus !? {
			this.out_(transbus)
		};
		outProxy.source = {
			In.ar(this.out, numChannels)
		};
		if (play) {
			outProxy.play(outbus);
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
		"synthDefName: %".format(synthDefName).postln;
		on !? { on.free };
		off !? { off.free };
		bend !? { bend.free };
		CVCenter.scv[keyboardDefName][synthDefName].do(_.release);
		CVCenter.scv[keyboardDefName].removeAt(synthDefName);
		CVCenter.scv.removeAt(keyboardDefName);
	}

	// add the sequencer, fed through sampling keyboard strokes
	addSampler {
		if (sampler.isNil) {
			sampler = CVCenterKeyboardSampler(keyboardDefName);
		} {
			"The given keyboard '%' has already an assigned sampler.".format(keyboardDefName).error;
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
