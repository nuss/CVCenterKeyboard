CVCenterKeyboardSelect {
	var <keyboardList;

	*new {
		^super.new.init;
	}

	init {
		keyboardList = CVCenterKeyboard.all.keys.asArray.sort;
		CVCenter.use(\keyboards, [0, keyboardList.size-1, \lin, 1.0, 0], 0, \default, svItems: keyboardList);
	}
}