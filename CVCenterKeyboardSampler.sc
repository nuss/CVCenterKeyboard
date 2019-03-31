// CVCenterKeyboardSampler {
// 	classvar <allSamplers;
// 	var <>name;
// 	var <sample = false;
// 	var <keyboard, <sampleEvents, <pdefs;
// 	var sampleStart, sampleEnd, <onTimes, <offTimes;
//
// 	*new { |name|
// 		^super.newCopyArgs(name).init;
// 	}
//
// 	init {
// 		allSamplers ?? {
// 			allSamplers = ();
// 		};
// 		if (allSamplers[name.asSymbol].notNil) {
// 			Error("A CVCenterKeyboardSampler under the name '%' already exists!".format(name)).throw;
// 		} {
// 			allSamplers.put(name.asSymbol, this);
// 		};
// 		sampleEvents = ()!128;
// 		pdefs = [];
// 	}
//
// 	// start/stop sampling
// 	sample_ { |onOff|
// 		var pbinds, items, pbproxy, last;
// 		sample = onOff;
// 		if (sample == false) {
// 			sampleEnd = Main.elapsedTime;
// 			sampleEvents.do { |e, num|
// 				// add last event - not considered within noteOn, notOff
// 				e.dur !? {
// 					// [num, this.prDurSum(e.dur)].postln;
// 					if (e.dur.last.isRest) {
// 						last = sampleEnd - onTimes[num];
// 					} {
// 						last = Rest(sampleEnd - offTimes[num]);
// 					};
// 					e.dur = e.dur.add(last);
// 					// [num, this.prDurSum(e.dur)].postln;
// 				}
// 			};
// 			pbinds = sampleEvents.collect { |slot, num|
// 				if (slot.isEmpty.not) {
// 					items = [\instrument, synthDefName, keyboardArg, num.midicps]
// 					++ slot.collect(Pseq(_, inf)).asPairs;
// 					/*++ slot.collect { |v, k|
// 					if (CVCenter.at((k.asString[0].toUpper ++ k.asString[1..]).asSymbol).notNil) {
// 					if (v.size < 2) {
// 					Pseq(CVCenter.at((k.asString[0].toUpper ++ k.asString[1..]).asSymbol).value_(v), inf)}
// 					}
// 					};*/
// 					// items.postcs; "\n\n".postln;
// 					pbproxy = Pbind.new.patternpairs_(items);
// 				}
// 			}./*postln.*/takeThese(_.isNil);
// 			// pbinds.do { |pb| pb.patternpairs.postln };
// 			pdefs = pdefs.add(Pdef((synthDefName ++ (pdefs.size)).asSymbol, Ppar(pbinds, inf)));
// 			pdefs.last.play;
// 			#sampleStart, sampleEnd = nil!2;
// 			"\nsampling keyboard events finished, should start playing now\n".inform;
// 		} {
// 			sampleStart = Main.elapsedTime;
// 			this.prResetSampling;
// 			"\nsampling keyboard events started\n".inform;
// 		}
// 	}
//
// 	prDurSum { |durs|
// 		^durs.sum { |num| if (num.isRest) { num.dur } { num }}
// 	}
//
// 	prResetSampling {
// 		// starttime, absolute
// 		#onTimes, offTimes = Main.elapsedTime!128!2;
// 		// the array holding all events for all 128 midi keys
// 		sampleEvents = ()!128;
// 	}
//
// 	clearSamples { |...indices|
// 		if (indices.isEmpty) {
// 			pdefs.do { |p| p.clear }
// 		} {
// 			indices.do { |i| pdefs[i].clear };
// 		}
// 	}
// }