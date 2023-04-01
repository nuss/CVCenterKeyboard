CVCenterKeyboardSampler {
	classvar <all;
	var keyboardDefName;
	var <groups;
	var isSampling = false;
	var sampleStart, sampleEnd, onTimes, offTimes;
	var sampleOnFunc, sampleOffFunc, sampleEvents;
	var <pdef;

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
		};
		sampleOffFunc = { |veloc, num, chan, src|
			offTimes[num] = Main.elapsedTime;
			sampleEvents[num].dur ?? {
				sampleEvents[num].put(\dur, []);
			};
			sampleEvents[num].dur = sampleEvents[num].dur.add(offTimes[num] - onTimes[num]);
		};
		CVCenterKeyboard.all[keyboardDefName].on.add(sampleOnFunc);
		CVCenterKeyboard.all[keyboardDefName].on.add(sampleOnFunc);
	}

	start {
		sampleStart = Main.elapsedTime;
	}

	stop { |synthDefName|
		if (synthDefName.notNil) {
			synthDefName = synthDefName.asSymbol;
		} {
			synthDefName = CVCenterKeyboard.all[keyboardDefName].currentSynthDef;
		};
		sampleEnd = Main.elapsedTime;
		pdef ?? { pdef = List[] };
	}
}