CVCenterKeyboardSelect {
	classvar <window;
	classvar <srcID, <keyboardList;

	*front {
		var pop;
		CVCenterKeyboard.all ?? {
			Error("Create at least one new CVCenterKeyboard before creating a select").throw;
		};
		window ?? {
			window = Window("Keyboards", Rect(0, 0, 350, 100));
			pop = PopUpMenu(window, window.view.bounds.insetBy(30));
			CVCenter.use(\keyboards, tab: \default, svItems: CVCenterKeyboard.all.keys.asArray.sort);
			CVCenter.at(\keyboards).connect(pop);
		};
		window.front
	}

	// srcID_ { |uid|
	// 	srcID = uid;
	// 	// TODO: set source ID select to uid
	// }
	//
	// front {
	// 	var pop;
	// 	if (window.isNil) {
	// 		window = Window("Keyboards", Rect(0, 0, 350, 100));
	// 		pop = PopUpMenu(window, window.view.bounds.insetBy(30));
	// 		CVCenter.at(name).connect(pop);
	// 	};
	// 	window.front;
	// }
}
