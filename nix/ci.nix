{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  name = "ci-shell";
  buildInputs = [
    pkgs.git
    pkgs.gradle
    pkgs.openjdk
    pkgs.androidsdk
    pkgs.android-studio
  ];
  shellHook = ''
    echo "Welcome to the CI shell!"
    echo "Running checks..."
    # Add your CI steps here, e.g.:
    # ./gradlew build
  '';
} 