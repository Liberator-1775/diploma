/* eslint-disable */
const HtmlWebpackPlugin = require("html-webpack-plugin");

module.exports = {
  entry: {
    popup: "./src/popup.ts",
    content: "./src/content.ts",
    background: "./src/background.ts",
  },
  output: {
    filename: "[name].js",
    path: __dirname + "/chrome",
  },
  module: {
    rules: [{ test: /\.ts$/, use: "ts-loader" }],
  },
  mode: "production",
  resolve: {
    extensions: [".ts"],
  },
};
