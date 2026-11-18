import { BottomSheetTextInput, useBottomSheetInternal } from "@gorhom/bottom-sheet";
import { cssInterop } from "nativewind";
import * as React from "react";
import { TextInput, type TextInputProps } from "react-native";

// The same class handling NativeWind gives React Native's TextInput.
cssInterop(BottomSheetTextInput, {
  className: { target: "style", nativeStyleToProp: { textAlign: true } },
});

/**
 * A TextInput that knows when it sits in a gorhom bottom sheet, the sheets
 * drawn on Android. There it is gorhom's BottomSheetTextInput, which tells
 * the sheet about focus so the sheet moves up with the keyboard; a plain
 * TextInput would stay under the keyboard. Elsewhere, including the native
 * iOS form sheets, it is React Native's TextInput.
 */
function SheetTextInput(props: TextInputProps) {
  const sheet = useBottomSheetInternal(true);
  return sheet ? <BottomSheetTextInput {...props} /> : <TextInput {...props} />;
}

export { SheetTextInput };
