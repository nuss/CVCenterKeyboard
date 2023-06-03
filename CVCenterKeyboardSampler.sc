CVCenterKeyboardSampler {
	classvar <all;
	var <keyboard;
	var <>touchOSC;
	var isSampling = false;
	var sampleStart, sampleEnd, onTimes, offTimes;
	var sampleOnFunc, sampleOffFunc, sampleEvents;
	var <pdef, cSample = 1;
	var <>debug = false;

	*initClass {
		all = ();
	}

	*new { |keyboard, touchOSC|
		^super.newCopyArgs(keyboard, touchOSC).init;
	}

	init {
		keyboard ?? {
			"No CVCenterKeyboard instance given!".error;
			^nil
		};
		if (keyboard.class != CVCenterKeyboard) {
			"The given keyboard doesn't appear to be a CVCenterKeyboard instance".error;
			^nil
		} {
			keyboard.sampler = this;
		};
		all.put(keyboard.keyboardDefName, this);
		sampleOnFunc = { |veloc, num, chan, src|
			var kbArgs, argsValues;
			var offTime;

			keyboard.currentSynthDef ?? {
				"CVCenterKeyboardSampler: No SynthDef selected for keyboard '%'. Call setSynthDef(synthDefName) on the CVCenterKeyboard instance before playing the keyboard!".format(keyboard.keyboardDefName).error;
			};
			if (keyboard.synthParams.isNil) {
				"No SynthDef initialized for keyboard yet. Call \"setUpControls\" on your CVCenterKeyboard instance first.".warn;
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
				"CVCenterKeyboardSampler: No SynthDef selected for keyboard '%'. Call setSynthDef(synthDefName) on the CVCenterKeyboard instance before playing the keyboard!".format(keyboard.keyboardDefName).error;
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
		this.touchOSC !? {
			CVCenter.use(\removeAllSequences, \false, tab: ("player: %".format(keyboard.keyboardDefName)).asSymbol);
			CVCenter.addActionAt(\removeAllSequences, 'remove all sequences', { |cv|
				if (cv.input.asBoolean) { this.clearSamples }
			});
			CVCenter.cvWidgets[\removeAllSequences].oscConnect(this.touchOSC.addr.ip, "/seq_remove_all");
		}
	}

	// maybe it would be more convenient to have only one method 'sample' with a parameter onOff
	sample { |onOff|
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
				pbinds.do { |pb| pb.patternpairs.postln };
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
				ampWdgtName = ("% amp".format(name)).asSymbol;
				pauseWdgtName = ("% pause".format(name)).asSymbol;
				removeWdgtName = ("% remove".format(name)).asSymbol;
				{
					CVCenter.use(ampWdgtName, \amp, 1.0, tab: ("player: " ++ keyboard.keyboardDefName).asSymbol);
					CVCenter.addActionAt(ampWdgtName, 'set sequence amp', { |cv| Ndef(name).set(\amp, cv.value )});
					CVCenter.use(pauseWdgtName, \false, tab:  ("player: " ++ keyboard.keyboardDefName).asSymbol);
					CVCenter.addActionAt(pauseWdgtName, 'pause/resume sequence', { |cv|
						if (cv.input.asBoolean) { Ndef(name).pause } { Ndef(name).resume }
					});
					CVCenter.use(removeWdgtName, \false, tab:  ("player: " ++ keyboard.keyboardDefName).asSymbol);
					CVCenter.addActionAt(removeWdgtName, 'remove sequence', { |cv|
						if (cv.input.asBoolean) { this.clearSamples(name) }
					});
					this.touchOSC !? {
						this.touchOSC.addr.sendMsg("/seq_%_name".format(cSample), name);
						this.touchOSC.addr.sendMsg("/seq_%_amp".format(cSample), 1.0);
						CVCenter.cvWidgets[ampWdgtName].oscConnect(this.touchOSC.addr.ip, name: "/seq_%_amp".format(cSample));
						CVCenter.cvWidgets[pauseWdgtName].oscConnect(this.touchOSC.addr.ip, name: "/seq_%_pause_resume".format(cSample));
						CVCenter.cvWidgets[removeWdgtName].oscConnect(this.touchOSC.addr.ip, name: "/seq_%_remove".format(cSample));
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

	clearSamples { |...keys|
		var i;

		if (pdef.notEmpty) {
			if (keys.isEmpty) {
				pdef.do { |p, i|
					p.source.clear;
					p.clear;
					this.touchOSC !? {
						this.touchOSC.addr.sendMsg("/seq_%_name".format(i+1), "");
						this.touchOSC.addr.sendMsg("/seq_%_amp".format(i+1), 0.0);
						this.touchOSC.addr.sendMsg("/seq_%_pause_resume".format(i+1), 0.0);
					}
				};
				pdef.removeAll;
				// reset counter
				cSample = 1;
				CVCenter.removeAtTab("player: %".format(keyboard.keyboardDefName).asSymbol);
			} {
				keys.do { |k|
					if (k.class == String) { k = k.asSymbol };
					if (k.isInteger) { k = pdef[k].key };
					i = pdef.indexOf(Ndef(k));
					Ndef(k).source.clear;
					Ndef(k).clear;
					this.touchOSC !? {
						this.touchOSC.addr.sendMsg("/seq_%_name".format(i+1), "");
						this.touchOSC.addr.sendMsg("/seq_%_amp".format(i+1), 0.0);
						this.touchOSC.addr.sendMsg("/seq_%_pause_resume".format(i+1), 0.0);
					};
					pdef.removeAt(i);
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