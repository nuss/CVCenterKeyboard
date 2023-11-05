CVCenterKeyboardRecorder {
	classvar <all;
	var <keyboard, tOSCtrackNums, usedTracks;
	var isSampling = false;
	var sampleStart, sampleEnd, onTimes, offTimes;
	var sampleOnFunc, sampleOffFunc, sampleEvents;
	var <pdef, cSample = 1;
	var <removeAllWdgtName;
	var <>debug = false;

	*initClass {
		all = ();
	}

	*new { |keyboard|
		^super.newCopyArgs(keyboard).init;
	}

	init {
		keyboard ?? {
			"[CVCenterKeyboardRecorder] No CVCenterKeyboard instance given!".error;
			^nil
		};
		if (keyboard.class != CVCenterKeyboard) {
			"[CVCenterKeyboardRecorder] The given keyboard doesn't appear to be a CVCenterKeyboard instance: %".format(keyboard.class).error;
			^nil
		};
		all.put(keyboard.keyboardDefName, this);
		removeAllWdgtName = "[%] remove all sequences".format(keyboard.keyboardDefName).asSymbol;
		CVCenter.use(removeAllWdgtName, \false, tab: "player: %".format(keyboard.keyboardDefName).asSymbol);
		CVCenter.addActionAt(
			removeAllWdgtName,
			'remove all sequences',
			"{ |cv|
				if (cv.input.asBoolean) { CVCenterKeyboardRecorder.all['%'].clearSequences }
			}".format(keyboard.keyboardDefName)
		);
		if (keyboard.touchOSC.notNil) {
			this.initRecorderTouchOSC;
		};
		sampleOnFunc = { |veloc, num, chan, src|
			var kbArgs, argsValues, noteIndex;
			var offTime;
			var synthDefSize = keyboard.currentSynthDef.size;

			keyboard.currentSynthDef ?? {
				"[CVCenterKeyboardRecorder] No SynthDef selected for keyboard '%'. Call setSynthDef(synthDefName) on the CVCenterKeyboard instance before playing the keyboard!".format(keyboard.keyboardDefName).error;
			};

			if (keyboard.synthParams.isNil) {
				"[CVCenterKeyboardRecorder] No SynthDef initialized for keyboard yet. Call \"setUpControls\" on your CVCenterKeyboard instance first.".warn;
			} {
				if (isSampling) {
					onTimes[num] = Main.elapsedTime;
					noteIndex = num % keyboard.keysBlockSize;
					keyboard.currentSynthDef.do { |sd, i|
						if (keyboard.noteMatches.isNil or: {
							keyboard.noteMatches[i].includesEqual(noteIndex)
						}) {
							kbArgs = [
								keyboard.synthParams[sd].pitchControl,
								num.midicps,
								keyboard.synthParams[sd].velocControl,
								veloc * 0.005,
								keyboard.synthParams[sd].outControl,
								keyboard.out;
							];
							argsValues = kbArgs ++ keyboard.valuePairs[sd];

							argsValues.pairsDo { |k, v|
								sampleEvents[sd][num][k] ?? {
									sampleEvents[sd][num].put(k, [])
								};
								if (v.size > 1) {
									// multichannel-expand arrayed args properly
									sampleEvents[sd][num][k] = sampleEvents[sd][num][k].add([v]);
								} {
									sampleEvents[sd][num][k] = sampleEvents[sd][num][k].add(v);
								};
								if (this.debug) { "%: %".format(sd, [k, v]).postln };
							};
							sampleEvents[sd][num].dur ?? {
								sampleEvents[sd][num].put(\dur, []);
							};
							sampleEvents[sd][num].dur = sampleEvents[sd][num].dur.add(Rest(offTime = onTimes[num] - offTimes[num]));
							if (this.debug) { "[%] Off time: %".format(sd, offTime).postln };
						}
					}
				}
			}
		};
		sampleOffFunc = { |veloc, num, chan, src|
			var onTime, noteIndex;

			// "\noff count: %\n\n".postf(offc);
			// offc = offc + 1;

			keyboard.currentSynthDef ?? {
				"[CVCenterKeyboardRecorder] No SynthDef selected for keyboard '%'. Call setSynthDef(synthDefName) on the CVCenterKeyboard instance before playing the keyboard!".format(keyboard.keyboardDefName).error;
			};

			if (isSampling) {
				noteIndex = num % keyboard.keysBlockSize;
				offTimes[num] = Main.elapsedTime;
				keyboard.currentSynthDef.do { |sd, i|
					if (keyboard.noteMatches.isNil or: {
						keyboard.noteMatches[i].includesEqual(noteIndex)
					}) {
						sampleEvents[sd][num].dur ?? {
							sampleEvents[sd][num].put(\dur, []);
						};
						sampleEvents[sd][num].dur = sampleEvents[sd][num].dur.add(onTime = offTimes[num] - onTimes[num]);
						if (this.debug) { "[%] On time: %".format(sd, onTime).postln };
					}
				}
			}
		};
		keyboard.on.add(sampleOnFunc);
		keyboard.off.add(sampleOffFunc);
	}

	initRecorderTouchOSC {
		CVCenter.cvWidgets[removeAllWdgtName].oscConnect(keyboard.touchOSC.addr.ip, name: keyboard.touchOSC.seqRemoveAllCmd);
		tOSCtrackNums = keyboard.touchOSC.class.trackNums.copy;
		usedTracks = ();
	}

	record { |onOff|
		var synthDefNames, synthParams;
		var pbproxy, pbinds, last, items;
		var ampWdgtName, pauseWdgtName, removeWdgtName;

		synthDefNames = keyboard.currentSynthDef;

		case
		{ isSampling == false or: { onOff == true }} {
			isSampling = true;
			sampleStart = Main.elapsedTime;
			this.prResetSampling;
		}
		{ isSampling == true or: { onOff == false }} {
			isSampling = false;
			// FIXME: how do we get the right instrument in the sampled sequences?
			sampleEnd = Main.elapsedTime;
			synthDefNames.do { |sd|
				synthParams = keyboard.synthParams[sd];
				pdef ?? { pdef = List[] };
				sampleEvents[sd].do { |e, num|
					// add last event - not considered within noteOn, notOff
					e.dur !? {
						if (e.dur.last.isRest) {
							last = sampleEnd - onTimes[num];
						} {
							last = Rest(sampleEnd - offTimes[num]);
						};
						e.dur = e.dur.add(last);
					};
				};
				pbinds = sampleEvents[sd].collect { |slot, num|
					if (slot.isEmpty.not) {
						items = [\instrument, sd, synthParams.pitchControl, num.midicps]
						++ slot.collect(Pseq(_, inf)).asPairs;
						pbproxy = Pbind.new.patternpairs_(items);
					}
				}.takeThese(_.isNil);
				if (pbinds.notEmpty) {
					var name, index;
					name = (sd ++ "-" ++ cSample).asSymbol;
					Ndef(name).mold(2, \audio, \elastic);
					Ndef(name)[0] = Pdef(name, Ppar(pbinds, inf));
					// Ndef(name)[1] = \filter -> { |in|
					// 	In.ar(in, 2).checkBadValues * \amp.kr(1, spec: \amp.asSpec)
					// };
					Ndef(name).play;
					pdef.add(Ndef(name));
					this.prAddSequenceWidgets(sd, name);
					"\nsampling keyboard events finished, should start playing now\n".inform;
				};
			};
			cSample = cSample + 1;
			#sampleStart, sampleEnd = nil!2;
		}
		{ "\nnothing recorded, please try again\n".inform }
	}

	prAddSequenceWidgets { |synthDef, name|
		var ampWdgtName = "[%] % amp".format(keyboard.keyboardDefName, name).asSymbol;
		var pauseWdgtName = "[%] % pause".format(keyboard.keyboardDefName, name ).asSymbol;
		var removeWdgtName = "[%] % remove".format(keyboard.keyboardDefName, name).asSymbol;
		fork({
			"count: %".format(cSample).postln;
			CVCenter.use(ampWdgtName, \amp, 1.0, tab: "player: %".format(keyboard.keyboardDefName).asSymbol);
			CVCenter.addActionAt(ampWdgtName, 'set seq amp', { |cv| Ndef(name).set(\amp, cv.value )});
			CVCenter.use(pauseWdgtName, \false, tab:  "player: " ++ keyboard.keyboardDefName).asSymbol;
			CVCenter.addActionAt(pauseWdgtName,
				'pause/resume sequence',
				{ |cv|
					if (cv.input.asBoolean) { Ndef(name).pause } { Ndef(name).resume }
				}
			);
			CVCenter.use(removeWdgtName, \false, tab: "player: %".format(keyboard.keyboardDefName).asSymbol);
			CVCenter.addActionAt(
				removeWdgtName,
				'remove sequence',
				{ |cv|
					"clearing sequence at '%'".format(name).postln;
					if (cv.input.asBoolean) { this.clearSequences(name) }
				}
			);
			keyboard.touchOSC !? {
				keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqNameCmds[tOSCtrackNums[0]], name);
				keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqAmpCmds[tOSCtrackNums[0]], 1.0);
				CVCenter.cvWidgets[ampWdgtName].oscConnect(keyboard.touchOSC.addr.ip, name: keyboard.touchOSC.seqAmpCmds[tOSCtrackNums[0]]);
				CVCenter.cvWidgets[pauseWdgtName].oscConnect(keyboard.touchOSC.addr.ip, name: keyboard.touchOSC.seqPauseResumeCmds[tOSCtrackNums[0]]);
				CVCenter.cvWidgets[removeWdgtName].oscConnect(keyboard.touchOSC.addr.ip, name: keyboard.touchOSC.seqRemoveCmds[tOSCtrackNums[0]]);
				usedTracks.put(name, tOSCtrackNums[0]);
				tOSCtrackNums.removeAt(0);
			};
			this.prAddCVActions(synthDef, name);
			// tOSCCount = tOSCCount + 1;
		}, AppClock);
	}

	prResetSampling {
		// starttime, absolute
		#onTimes, offTimes = Main.elapsedTime!128!2;
		// the array holding all events for all 128 midi keys
		// for each of the SynthDefs denoted by currentSynthDef
		sampleEvents = ();
		keyboard.currentSynthDef.do { |name|
			sampleEvents.put(name, ()!128);
		}
	}

	clearSequences { |...keys|
		var i;

		if (keys.isEmpty) {
			if (pdef.notNil and: { pdef.notEmpty }) {
				pdef.do { |p|
					i = p.key.asString.split($-).last.asInteger;
					p.source.clear;
					p.clear;
					fork ({
						"removing CVCenter widget '[%] % amp'".format(keyboard.keyboardDefName, p.key).inform;
						CVCenter.removeAt(("[%] % amp".format(keyboard.keyboardDefName, p.key)).asSymbol);
						"removing CVCenter widget '[%] % pause'".format(keyboard.keyboardDefName, p.key).inform;
						CVCenter.removeAt(("[%] % pause".format(keyboard.keyboardDefName, p.key)).asSymbol);
						"removing CVCenter widget '[%] % remove'".format(keyboard.keyboardDefName, p.key).inform;
						CVCenter.removeAt(("[%] % remove".format(keyboard.keyboardDefName, p.key)).asSymbol);
					}, AppClock)
				};
				pdef.clear;
				cSample = 1;
			};
			// reset counter
			keyboard.touchOSC !? {
				fork({
					keyboard.touchOSC.class.trackNums.do { |num|
						"resetting TouchOSC sequence display at index %".format(num).inform;
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqNameCmds[num], "");
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqAmpCmds[num], 0.0);
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqPauseResumeCmds[num], 0.0);
						0.1.wait;
					};
				}, AppClock);
				tOSCtrackNums = keyboard.touchOSC.class.trackNums.copy;
				usedTracks = ();
			};
		} {
			keys.do { |k|
				if (pdef.notNil and: { pdef.notEmpty }) {
					if (k.class == String) { k = k.asSymbol };
					if (k.isInteger) { k = pdef[k].key };
					i = pdef.indexOf(Ndef(k));
					Ndef(k).source.clear;
					Ndef(k).clear;
					pdef.removeAt(i);
					if (pdef.size == 0) { cSample = 1 };
				};
				keyboard.touchOSC !? {
					"resetting TouchOSC sequence display at index %".format(i).inform;
					keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqNameCmds[usedTracks[k]].postln, "");
					keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqAmpCmds[usedTracks[k]].postln, 0.0);
					keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqPauseResumeCmds[usedTracks[k]].postln, 0.0);
					tOSCtrackNums = tOSCtrackNums.add(keyboard.touchOSC.class.trackNums[i]);
					usedTracks[k] = nil;
				};
				fork({
					"removing CVCenter widget '[%] % amp'".format(keyboard.keyboardDefName, k).inform;
					CVCenter.removeAt(("[%] % amp".format(keyboard.keyboardDefName, k)).asSymbol);
					"removing CVCenter widget '[%] % pause'".format(keyboard.keyboardDefName, k).inform;
					CVCenter.removeAt(("[%] % pause".format(keyboard.keyboardDefName, k)).asSymbol);
					"removing CVCenter widget '[%] % remove'".format(keyboard.keyboardDefName, k).inform;
					CVCenter.removeAt(("[%] % remove".format(keyboard.keyboardDefName, k)).asSymbol);
				}, AppClock)
			}
		}
	}

	prAddCVActions { |synthDefName, name|
		var thisWdgtsAndCtrls = keyboard.namesCVs[synthDefName].clump(3);
		thisWdgtsAndCtrls.do { |col|
			// col is a group of 3 parameters:
			// 0: the widget's name
			// 1: the arg to be set
			// 2: the widget's CV(s) - if it's an Array it's a CVWidget2D
			if (col[2].isArray) {
				CVCenter.addActionAt(col[0], "set Ndef('%')".format(name).asSymbol, { |cv|
					Ndef(name).group.set(col[1], [cv.value, CVCenter.at(col[0]).hi.value])
				}, \lo);
				CVCenter.addActionAt(col[0], "set Ndef('%')".format(name).asSymbol, { |cv|
					Ndef(name).group.set(col[1], [CVCenter.at(col[0]).lo.value, cv.value])
				}, \hi);
			} {
				CVCenter.addActionAt(col[0], "set Ndef('%')".format(name).asSymbol, { |cv|
					Ndef(name).group.set(col[1], cv.value)
				});
			}
		}
	}
}