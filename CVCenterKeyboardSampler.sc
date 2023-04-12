CVCenterKeyboardSampler {
	classvar <all;
	var keyboardDefName;
	var <groups;
	var isSampling = false;
	var sampleStart, sampleEnd, onTimes, offTimes;
	var sampleOnFunc, sampleOffFunc, sampleEvents;
	var <pdef, cSample = 1;

	*initClass {
		all = List[];
	}

	*new { |keyboardDefName|
		^super.newCopyArgs(keyboardDefName).init;
	}

	init {
		groups = List[];
		sampleOnFunc = { |veloc, num, chan, src|
			var keyboard = CVCenterKeyboard.all[keyboardDefName];
			var argsValues;
			var kbArgs = [
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
				sampleEvents[num].dur = sampleEvents[num].dur.add(Rest(onTimes[num] - offTimes[num]));
			}
		};
		sampleOffFunc = { |veloc, num, chan, src|
			if (isSampling) {
				offTimes[num] = Main.elapsedTime;
				sampleEvents[num].dur ?? {
					sampleEvents[num].put(\dur, []);
				};
				sampleEvents[num].dur = sampleEvents[num].dur.add(offTimes[num] - onTimes[num]);
			}
		};
		CVCenterKeyboard.all[keyboardDefName].on.add(sampleOnFunc);
		CVCenterKeyboard.all[keyboardDefName].off.add(sampleOffFunc);
	}

	// maybe it would be more convenient to have only one method 'sample' with a parameter onOff
	start {
		sampleStart = Main.elapsedTime;
		isSampling = true;
	}

	stop {
		var synthDefName = CVCenterKeyboard.all[keyboardDefName].currentSynthDef;
		var synthParams = CVCenterKeyboard.all[keyboardDefName].synthParams;
		var pbproxy, pbinds, name, group, last, items;

		isSampling = false;
		sampleEnd = Main.elapsedTime;
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
			groups.add(group = ParGroup.new);
			if (slot.isEmpty.not) {
				items = [\instrument, synthDefName, \group, group, synthParams[synthDefName].pitchControl, num.midicps]
				++ slot.collect(Pseq(_, inf)).asPairs;
				pbproxy = Pbind.new.patternpairs_(items);
			}
		}.takeThese(_.isNil);
		if (pbinds.isEmpty.not) {
			// pbinds.do { |pb| pb.patternpairs.postln };
			// pdef.add(Pdef((synthDefName ++ "-" ++ (pdef.size)).asSymbol, Ppar(pbinds, inf)));
			name = (synthDefName ++ "-" ++ cSample).asSymbol;
			Ndef(name).mold(2, \audio, \elastic);
			Ndef(name).source = Pdef(name, Ppar(pbinds, inf));
			pdef.add(Ndef(name));

			pdef.last.play(group: group);
			#sampleStart, sampleEnd = nil!2;
			cSample = cSample + 1;
			"\nsampling keyboard events finished, should start playing now\n".inform;
		} {
			"\nnothing recorded, please try again\n".inform;
		}
	}
}