CVCenterKeyboardSelect {
	classvar <allSelects;
	var <srcID, <keyboardList;
	var <window;

	*new { |srcID|
		^super/*.newCopyArgs(srcID)*/.new.init;
	}

	init {
		keyboardList = CVCenterKeyboard.allKeyboards.keys.asArray.sort;
		CVCenter.use(\keyboards, [0, keyboardList.lastIndex, \lin, 1.0, 0], 0, \default, svItems: keyboardList);
	}

	srcID_ { |uid|
		srcID = uid;
		// TODO: set source ID select to uid
	}

	front {
		if (window.notNil and: { window.isClosed.not }) {
			window.front;
		} {
			window = Window()
		}
	}
}