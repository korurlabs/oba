module.exports = function (api) {
  api.cache(true);
  // Reanimated 4 needs no `react-native-reanimated/plugin` entry: worklets are
  // transformed by babel-preset-expo with react-native-worklets, and adding the
  // legacy plugin registers it twice.
  return {
    presets: [["babel-preset-expo", { jsxImportSource: "nativewind" }], "nativewind/babel"],
  };
};
