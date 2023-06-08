CVCenterKeyboardRecorder {
	classvar <all;
	var <keyboard;
	var isSampling = false;
	var sampleStart, sampleEnd, onTimes, offTimes;
	var sampleOnFunc, sampleOffFunc, sampleEvents;
	var <pdef, cSample = 1;
	var <>debug = false;

	*initClass {
		all = ();
	}

	*new { |keyboard|
		^super.newCopyArgs(keyboard).init;
	}

	init {
		var removeAllWdgtName;

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
			CVCenter.cvWidgets[removeAllWdgtName].oscConnect(keyboard.touchOSC.addr.ip, name: keyboard.touchOSC.seqRemoveAllCmd);
		};
		sampleOnFunc = { |veloc, num, chan, src|
			var kbArgs, argsValues;
			var offTime;

			keyboard.currentSynthDef ?? {
				"[CVCenterKeyboardRecorder] No SynthDef selected for keyboard '%'. Call setSynthDef(synthDefName) on the CVCenterKeyboard instance before playing the keyboard!".format(keyboard.keyboardDefName).error;
			};
			if (keyboard.synthParams.isNil) {
				"[CVCenterKeyboardRecorder] No SynthDef initialized for keyboard yet. Call \"setUpControls\" on your CVCenterKeyboard instance first.".warn;
			} {
				kbArgs = [
					keyboard.synthParams[keyboard.currentSynthDef].pitchControl,
					num.midicps,
					keyboard.synthParams[keyboard.currentSynthDef].velocControl,
					veloc * 0.005,
					keyboard.synthParams[keyboard.currentSynthDef].outControl,
					// FIXME: this should probably be this.out, shouldn't it?
					// synthParams[synthDefName].out
					keyboard.out;
				];
				argsValues = kbArgs ++ keyboard.valuePairs;
				if (isSampling) {
					onTimes[num] = Main.elapsedTime;
					argsValues.pairsDo { |k, v|
						sampleEvents[num][k] ?? {
							if (this.debug) { [k, v].postln };
							sampleEvents[num].put(k, [])
						};
						if (v.size > 1) {
							// multichannel-expand arrayed args properly
							sampleEvents[num][k] = sampleEvents[num][k].add([v]);
						} {
							sampleEvents[num][k] = sampleEvents[num][k].add(v);
						}
					};
					sampleEvents[num].dur ?? {
						sampleEvents[num].put(\dur, []);
					};
					sampleEvents[num].dur = sampleEvents[num].dur.add(Rest(offTime = onTimes[num] - offTimes[num]));
					if (this.debug) { "Off time: %".format(offTime).postln };
				}
			}
		};
		sampleOffFunc = { |veloc, num, chan, src|
			var onTime;
			keyboard.currentSynthDef ?? {
				"[]CVCenterKeyboardRecorder] No SynthDef selected for keyboard '%'. Call setSynthDef(synthDefName) on the CVCenterKeyboard instance before playing the keyboard!".format(keyboard.keyboardDefName).error;
			};
			if (isSampling) {
				offTimes[num] = Main.elapsedTime;
				sampleEvents[num].dur ?? {
					sampleEvents[num].put(\dur, []);
				};
				sampleEvents[num].dur = sampleEvents[num].dur.add(onTime = offTimes[num] - onTimes[num]);
				if (this.debug) { "On time: %".format(onTime).postln };
			}
		};
		keyboard.on.add(sampleOnFunc);
		keyboard.off.add(sampleOffFunc);
	}

	// maybe it would be more convenient to have only one method 'record' with a parameter onOff
	record { |onOff|
		var synthDefName, synthParams;
		var pbproxy, pbinds, /*name,*/ /*group, */last, items/*, index*/;
		var ampWdgtName, pauseWdgtName, removeWdgtName;

		case
		{ isSampling == false or: { onOff == true }} {
			isSampling = true;
			sampleStart = Main.elapsedTime;
			this.prResetSampling;
		}
		{ isSampling == true or: { onOff == false }} {
			isSampling = false;
			sampleEnd = Main.elapsedTime;
			synthDefName = keyboard.currentSynthDef;
			synthParams = keyboard.synthParams;
			pdef ?? { pdef = List[] };
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
				// slot.pairsDo { |k, v| [k, v].postln };
				if (slot.isEmpty.not) {
					items = [\instrument, synthDefName, synthParams[synthDefName].pitchControl, num.midicps]
					++ slot.collect(Pseq(_, inf)).asPairs;
					pbproxy = Pbind.new.patternpairs_(items);
				}
			}.takeThese(_.isNil);
			if (pbinds.notEmpty) {
				var name, index;
				// pbinds.do { |pb| pb.patternpairs.postln };
				// pdef.add(Pdef((synthDefName ++ "-" ++ (pdef.size)).asSymbol, Ppar(pbinds, inf)));
				name = (synthDefName ++ "-" ++ cSample).asSymbol;
				Ndef(name).mold(2, \audio, \elastic);
				Ndef(name)[0] = Pdef(name, Ppar(pbinds, inf));
				// Ndef(name)[1] = \filter -> { |in|
				// 	In.ar(in, 2).checkBadValues * \amp.kr(1, spec: \amp.asSpec)
				// };
				Ndef(name).play;
				pdef.add(Ndef(name));
				#sampleStart, sampleEnd = nil!2;
				ampWdgtName = "[%] % amp".format(keyboard.keyboardDefName, name).asSymbol;
				pauseWdgtName = "[%] % pause".format(keyboard.keyboardDefName, name ).asSymbol;
				removeWdgtName = "[%] % remove".format(keyboard.keyboardDefName, name).asSymbol;
				{
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
							if (cv.input.asBoolean) { this.clearSequences(name) }
						}
					);
					keyboard.touchOSC !? {
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqNameCmds[cSample-1], name);
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqAmpCmds[cSample-1], 1.0);
						CVCenter.cvWidgets[ampWdgtName].oscConnect(keyboard.touchOSC.addr.ip, name: keyboard.touchOSC.seqAmpCmds[cSample-1]);
						CVCenter.cvWidgets[pauseWdgtName].oscConnect(keyboard.touchOSC.addr.ip, name: keyboard.touchOSC.seqPauseResumeCmds[cSample-1]);
						CVCenter.cvWidgets[removeWdgtName].oscConnect(keyboard.touchOSC.addr.ip, name: keyboard.touchOSC.seqRemoveCmds[cSample-1]);
					};
					cSample = cSample + 1;
					this.prAddCVActions(synthDefName, name);
					"\nsampling keyboard events finished, should start playing now\n".inform;
				}.defer
			} {
				"\nnothing recorded, please try again\n".inform;
			}
		}
	}

	prResetSampling {
		// starttime, absolute
		#onTimes, offTimes = Main.elapsedTime!128!2;
		// the array holding all events for all 128 midi keys
		sampleEvents = ()!128;
	}

	clearSequences { |...keys|
		var i;

		if (pdef.notNil and: { pdef.notEmpty }) {
			if (keys.isEmpty) {
				pdef.do { |p|
					i = p.key.asString.split($-).last.asInteger;
					p.source.clear;
					p.clear;
					keyboard.touchOSC !? {
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqNameCmds[i-1], "");
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqAmpCmds[i-1], 0.0);
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqPauseResumeCmds[i-1], 0.0);
					};
					{
						CVCenter.removeAt(("[%] % amp".format(keyboard.keyboardDefName, p.key)).asSymbol);
						CVCenter.removeAt(("[%] % pause".format(keyboard.keyboardDefName, p.key)).asSymbol);
						CVCenter.removeAt(("[%] % remove".format(keyboard.keyboardDefName, p.key)).asSymbol);
					}.defer;
				};
				pdef.clear;
				// reset counter
				cSample = 1;
			} {
				keys.do { |k|
					if (k.class == String) { k = k.asSymbol };
					if (k.isInteger) { k = pdef[k].key };
					i = pdef.indexOf(Ndef(k));
					Ndef(k).source.clear;
					Ndef(k).clear;
					keyboard.touchOSC !? {
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqNameCmds[i], "");
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqAmpCmds[i], 0.0);
						keyboard.touchOSC.addr.sendMsg(keyboard.touchOSC.seqPauseResumeCmds[i], 0.0);
					};
					pdef.removeAt(i);
					{
						CVCenter.removeAt(("[%] % amp".format(keyboard.keyboardDefName, k)).asSymbol);
						CVCenter.removeAt(("[%] % pause".format(keyboard.keyboardDefName, k)).asSymbol);
						CVCenter.removeAt(("[%] % remove".format(keyboard.keyboardDefName, k)).asSymbol);
					}.defer;
					if (pdef.size == 0) { cSample = 1 };
				};
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