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

		allSelects[keyboardDef] !? {
			Error("A key board select for the given keyboard definition already exists!").throw;
		};

		if (keyboardDef.isNil) {
			Error("No keyboard definition given. Please create a new CVCenterKeyboard first.").throw;
		} {
			if (keyboardDef.class !== CVCenterKeyboard) {
				Error("A CVCenterKeyboardSelect can only be created for a CVCenterKeyboard instance.").throw;
			} {
				kbDefName = keyboardDef.keyboardDefName;
				synthList = keyboardDef.synthDefNames.asArray;
				CVCenter.use(kbDefName, tab: tab, svItems: synthList ?? {
					['no stored keyboards found']
				});
				keyboardDef.synthDefNames.addDependant(
					{ CVCenter.at(kbDefName).items_(keyboardDef.synthDefNames.asArray) };
				);
			}
		}
	}

	front {
		var pop;

		window ?? {
			window = Window(kbDefName, Rect(0, 0, 350, 100));
			pop = PopUpMenu(window, window.view.bounds.insetBy(30));
			CVCenter.at(kbDefName).connect(pop);
		};
		window.front
	}

	addOSC { |extIP = "192.168.1.2", listenerPort = "9000", prevSynthCmd = "/prev", nextSynthCmd = "/next", nameCmd = "/set_keyboard_name"|
		var selectKB;
		var listenerAddr = NetAddr(extIP, listenerPort);

		CVCenter.cvWidgets[kbDefName] !? {
			this.removeOSC;

			selectKB = "{ |cv|
				var nameWdgt = CVCenter.at('%');
				nameWdgt.value_((nameWdgt.value + cv.value).mod(nameWdgt.items.size));
			}".format(kbDefName);
			CVCenter.use((kbDefName ++ ' next').asSymbol, \increment, tab: tab);
			CVCenter.use((kbDefName ++ ' prev').asSymbol, \decrement, tab: tab);
			CVCenter.cvWidgets[(kbDefName ++ ' next').asSymbol].addAction((kbDefName ++ ' next').asSymbol, selectKB);
			CVCenter.cvWidgets[(kbDefName ++ ' prev').asSymbol].addAction((kbDefName ++ ' prev').asSymbol, selectKB);

			CVCenter.cvWidgets[(kbDefName ++ ' next').asSymbol].oscConnect(
				listenerAddr.ip, nil, nextSynthCmd
			);
			CVCenter.cvWidgets[(kbDefName ++ ' prev').asSymbol].oscConnect(
				listenerAddr.ip, nil, prevSynthCmd
			);

			CVCenter.cvWidgets[kbDefName].addAction('set name', "{ |cv|
				var nameCmd = '%';\n
				var listenerAddr = %;\n
				listenerAddr.sendMsg(nameCmd, cv.items[cv.value]);
			}".format(nameCmd, listenerAddr.asCompileString));
		}
	}

	removeOSC {
		var wdgt = CVCenter.cvWidgets[kbDefName];
		var up = CVCenter.cvWidgets[kbDefName ++ ' next'];
		var down = CVCenter.cvWidgets[kbDefName ++ ' prev'];

		wdgt !? {
			if (wdgt.wdgtActions.notNil and:{ wdgt.wdgtActions['set name'].notNil }) {
				CVCenter.cvWidgets[kbDefName].removeAction('set name')
			}
		};

		up !? { up.oscDisconnect };
		down !? { down.oscDisconnect };
	}

}
