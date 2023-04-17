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
	sample {
		var synthDefName, synthParams;
		var pbproxy, pbinds, name, group, last, items;

		if (isSampling.not) {
			sampleStart = Main.elapsedTime;
			this.prResetSampling;
			isSampling = true;
		} {
			synthDefName = CVCenterKeyboard.all[keyboardDefName].currentSynthDef;
			synthParams = CVCenterKeyboard.all[keyboardDefName].synthParams;
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
			groups.add(group = ParGroup.new);
			pbinds = sampleEvents.collect { |slot, num|
				// slot.pairsDo { |k, v| [k, v].postln };
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
				this.prAddCVActions(synthDefName, groups.indexOf(group));
				"\nsampling keyboard events finished, should start playing now\n".inform;
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

	clearSamples { |...indices|
		if (indices.isEmpty) {
			pdef.do { |p, i|
				p.source.clear;
				pdef.remove(p);
			}
		} {
			indices.do { |i|
				pdef[i].source.clear;
				pdef.removeAt(i);
			};
		}
	}

	prAddCVActions { |synthDefName, groupIndex|
		var thisWdgtsAndCtrls = CVCenterKeyboard.all[keyboardDefName].namesCVs[synthDefName].clump(3);
		thisWdgtsAndCtrls.do { |col|
			// col is a group of 3 parameters:
			// 0: the widget's name
			// 1: the arg to be set
			// 2: the widget's CV
			if (col[1].isArray) {
				// FIXME: possibly 'all' should be a Dictionary
				CVCenter.addActionAt(col[0], "set group%".format(groupIndex).asSymbol, "{ |cv|
					CVCenterKeyboardSampler.all[%].groups[%].set('%', [cv.value, CVCenter.at('%').hi.value])
				}".format(this.class.all.indexOF(this), groupIndex, col[1], col[0]), \lo);
				CVCenter.addActionAt(col[0], "set group%".format(groupIndex).asSymbol, "{ |cv|
					CVCenterKeyboardSampler.all[%].groups[%].set('%', [CVCenter.at('%').lo.value, cv.value])
				}".format(this.class.all.indexOF(this), groupIndex, col[1], col[0]), \hi);
			} {
				CVCenter.addActionAt(col[0], "set group%".format(groupIndex).asSymbol, "{ |cv|
					CVCenterKeyboardSampler.all[%].groups[%].set('%', cv.value)
				}".format(this.class.all.indexOF(this), groupIndex, col[1]));
			}
		}
	}
}