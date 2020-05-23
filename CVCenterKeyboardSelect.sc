CVCenterKeyboardSelect {
	classvar <allSelects;
	var <keyboardDef;
	var <>prevSynthCmd = "/prev", <>nextSynthCmd = "/next", <>nameCmd = "/set_keyboard_name";
	var <window, <synthList, <tab = \default;
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

	// pass in an existing instance of CVCenterKeyboard
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

			selectKB = { |cv|
				CVCenter.at(kbDefName).value_((CVCenter.at(kbDefName).value + cv.value) % synthList.size)
			};
			CVCenter.use((kbDefName + 'next kb').asSymbol, \increment, tab: tab);
			CVCenter.use((kbDefName + 'prev kb').asSymbol, \decrement, tab: tab);
			// CVCenter.at((kbDefName + 'next kb').asSymbol) !? {
				CVCenter.cvWidgets[(kbDefName + 'next kb').asSymbol].addAction((kbDefName + 'next kb').asSymbol, selectKB);
		// };
			// CVCenter.at((kbDefName + 'prev kb').asSymbol) !? {
				CVCenter.cvWidgets[(kbDefName + 'prev kb').asSymbol].addAction((kbDefName + 'prev kb').asSymbol, selectKB);
		// };

			CVCenter.cvWidgets[(kbDefName + 'next kb').asSymbol].oscConnect(
				listenerAddr.ip, nil, nextSynthCmd
			);
			CVCenter.cvWidgets[(kbDefName + 'prev kb').asSymbol].oscConnect(
				listenerAddr.ip, nil, prevSynthCmd
			);

			CVCenter.cvWidgets[kbDefName].addAction('set name', { |cv|
				listenerAddr.sendMsg(nameCmd, cv.items[cv.value])
			});
		}
	}

	removeOSC {
		var wdgt = CVCenter.cvWidgets[kbDefName];
		wdgt !? {
			if (wdgt.wdgtActions.notNil and:{ wdgt.wdgtActions['set name'].notNil }) {
				CVCenter.cvWidgets[kbDefName].removeAction('set name')
			}
		}
	}

}
