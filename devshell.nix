{ pkgs }:

let
  inherit (pkgs) devshell android-sdk gradle jdk android-studio;
  conditionalPackages = if pkgs.system != "aarch64-darwin" then [ android-studio ] else [ ];
in

devshell.mkShell {
  name = "lynxproject";

  motd = ''
    🌿 Welcome to the Lynx Development Environment 🌿
  '';

  env = [
    { name = "ANDROID_HOME"; value = "${android-sdk}/share/android-sdk"; }
    { name = "ANDROID_SDK_ROOT"; value = "${android-sdk}/share/android-sdk"; }
    # Ensure jdk.home points to JDK 21 for JAVA_HOME
    { name = "JAVA_HOME"; value = jdk.home; }
  ];

  packages = [
    android-sdk
    gradle
    jdk
  ] ++ conditionalPackages;
}

