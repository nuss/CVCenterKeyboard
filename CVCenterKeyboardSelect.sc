CVCenterKeyboardSelect {
	classvar <allSelects;
	var <keyboardDef;
	var <window, <tab = \default;
	var kbDefName = \keyboard, dependantFunc;

	*initClass {
		allSelects = ();
		Class.initClassTree(Spec);
		Spec.add(\increment, #[0, 1, \lin, 1.0, 0]);
		Spec.add(\decrement, #[0, -1, \lin, 1.0, 0]);
	}

	*new { |keyboardDef, tab = \default|
		^super.newCopyArgs(keyboardDef).init(tab);
	}

	init { |tab|
		var synthList;

		if (keyboardDef.isNil) {
			Error("No keyboard definition given. Please create a new CVCenterKeyboard first.").throw;
		} {
			if (keyboardDef.class !== CVCenterKeyboard) {
				Error("A CVCenterKeyboardSelect can only be created for a CVCenterKeyboard instance.").throw;
			} {
				kbDefName = keyboardDef.keyboardDefName;
				if (allSelects[kbDefName].notNil) {
					Error("A keyboard select for the given keyboard definition already exists!").throw;
				} {
					allSelects.put(kbDefName, this);
					synthList = keyboardDef.synthDefNames.asArray;
					CVCenter.use(kbDefName, tab: tab, svItems: synthList ?? {
						['no stored keyboards found']
					});
					CVCenter.addActionAt(kbDefName, 'set keyboard synth', "{ |cv|
						CVCenterKeyboard.all['%'].switchSynthDef(cv.items[cv.value])
					}".format(kbDefName));
					keyboardDef.synthDefNames.addDependant(
						{ CVCenter.at(kbDefName).items_(keyboardDef.synthDefNames.asArray) };
					);
				}
			}
		}
	}

	front {
		var pop;

		if (window.isNil or: { window.isClosed }) {
			window = Window(kbDefName, Rect(0, 0, 350, 100));
			pop = PopUpMenu(window, window.view.bounds.insetBy(30));
			CVCenter.at(kbDefName).connect(pop);
		};
		window.front
	}

	addOSC { |extIP = "192.168.1.2", listenerPort = "9000", prevSynthCmd = "/prev_kb", nextSynthCmd = "/next_kb", nameCmd = "/set_kb_name", prefix = 1|
		var selectKB;
		var listenerAddr = NetAddr(extIP, listenerPort);

		if (prefix.notNil) {
			prefix = "/" ++ prefix;
		} {
			prefix = "";
		};

		CVCenter.cvWidgets[kbDefName] !? {
			this.removeOSC;

			selectKB = "{ |cv|
				var nameWdgt = CVCenter.at('%');
				var keyboardName = '%';
				var synthDefName = (nameWdgt.value + cv.value).mod(nameWdgt.items.size);
				nameWdgt.value_(synthDefName);
				CVCenterKeyboard.all[keyboardName].switchSynthDef(synthDefName);
				CVCenterKeyboard.all[keyboardName].reInit;
			}".format(kbDefName, this.keyboardDef.keyboardDefName);
			CVCenter.use((kbDefName ++ ' next').asSymbol, \increment, tab: tab);
			CVCenter.use((kbDefName ++ ' prev').asSymbol, \decrement, tab: tab);
			// add a button for freeing hanging nodes
			CVCenter.use((kbDefName ++ ' free').asSymbol, \false, tab: tab);
			CVCenter.cvWidgets[(kbDefName ++ ' free').asSymbol].addAction('free hanging nodes', "{ |cv|
				if (cv.input.asBoolean) {
					CVCenterKeyboard.all['%'].freeHangingNodes;
				}
			}".format(this.keyboardDef.keyboardDefName));
			CVCenter.cvWidgets[(kbDefName ++ ' next').asSymbol].addAction((kbDefName ++ ' next').asSymbol, selectKB);
			CVCenter.cvWidgets[(kbDefName ++ ' prev').asSymbol].addAction((kbDefName ++ ' prev').asSymbol, selectKB);

			CVCenter.cvWidgets[(kbDefName ++ ' next').asSymbol].oscConnect(
				listenerAddr.ip, nil, prefix ++ nextSynthCmd
			);
			CVCenter.cvWidgets[(kbDefName ++ ' prev').asSymbol].oscConnect(
				listenerAddr.ip, nil, prefix ++ prevSynthCmd
			);
			CVCenter.cvWidgets[(kbDefName ++ ' free').asSymbol].oscConnect(
				listenerAddr.ip, nil, prefix ++ "/kb_free_hanging_nodes"
			);

			CVCenter.cvWidgets[kbDefName].addAction('set name', "{ |cv|
				var nameCmd = '%';\n
				var listenerAddr = %;\n
				listenerAddr.sendMsg(%nameCmd, cv.items[cv.value]);
			}".format(nameCmd, listenerAddr.asCompileString, prefix));
		}
	}

	removeOSC {
		var wdgt = CVCenter.cvWidgets[kbDefName];
		var up = CVCenter.cvWidgets[(kbDefName ++ ' next').asSymbol];
		var down = CVCenter.cvWidgets[(kbDefName ++ ' prev').asSymbol];

		wdgt !? {
			if (wdgt.wdgtActions.notNil and:{ wdgt.wdgtActions['set name'].notNil }) {
				CVCenter.cvWidgets[kbDefName].removeAction('set name')
			}
		};

		up !? { up.oscDisconnect };
		down !? { down.oscDisconnect };
	}

}
