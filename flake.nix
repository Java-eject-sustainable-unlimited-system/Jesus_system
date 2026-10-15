{
  description = "J.E.S.U.S System & eGovFrame Omni-Platform Arena (Linux, macOS, Windows & Windows Server)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs, ... }:
    let
      supportedSystems = [ "aarch64-darwin" "aarch64-linux" "x86_64-linux" "x86_64-windows" ];
      forEachSystem = f: nixpkgs.lib.genAttrs supportedSystems (system: f {
        pkgs = import nixpkgs { inherit system; };
      });

      # Reproducibility hash of the vendored Maven dependency repository
      # (fixed-output derivation → no network access during the actual build).
      # Regenerate after Maven dependency changes with the Nix fixed-output hash from the build.
      mvnHash = "sha256-aRAw4R5TH4MelJ+QqJOMOHoH4Osd1RHbabH1AxayvGg=";

      # Shared builder: bit-reproducible Maven build with hash-pinned dependencies.
      buildJesusServer = { pkgs, jdk, pname, adaVm ? null, rustVm ? null }:
        let
          vmEnvironment = pkgs.lib.optionalAttrs (adaVm != null && rustVm != null) {
            JESUS_ADA_VM = "${adaVm}/bin/ada_jvm";
            JESUS_RUST_VM = "${rustVm}/bin/jesus-rust-vm";
          };
          vmFlags = pkgs.lib.optionalString (adaVm != null && rustVm != null)
            "--set JESUS_ADA_VM ${adaVm}/bin/ada_jvm --set JESUS_RUST_VM ${rustVm}/bin/jesus-rust-vm";
        in pkgs.maven.buildMavenPackage {
          inherit pname;
          version = "1.0.0";
          src = ./.;
          mvnJdk = jdk;
          inherit mvnHash;
          nativeBuildInputs = [ pkgs.makeWrapper ];
          env = vmEnvironment;
          mvnFetchExtraArgs.env = vmEnvironment;
          installPhase = ''
            mkdir -p $out/bin $out/share
            cp target/*-exec.jar $out/share/app.jar
            makeWrapper ${jdk}/bin/java $out/bin/run-jesus \
              --add-flags "-jar $out/share/app.jar" ${vmFlags}
          '';
          # Enables `nix run .#<pname-suffix>` used by the resurrection protocol.
          meta.mainProgram = "run-jesus";
        };
    in {
      # 1. 멀티 플랫폼 로컬 개발 및 관리 셸 (devShells)
      devShells = forEachSystem ({ pkgs }: {
        default = pkgs.mkShell {
          buildInputs = with pkgs; [ jdk17 maven gradle git jq ];
          shellHook = ''
            echo "=================================================="
            echo " [✝️] J.E.S.U.S Arena: Java 17 (Legacy / Win-Server) "
            echo " Target System   : ${pkgs.stdenv.system}"
            echo " Java Version    : $(java -version 2>&1 | head -n 1)"
            echo "=================================================="
          '';
        };

        java25 = pkgs.mkShell {
          buildInputs = with pkgs; [ jdk25 maven gradle git jq ];
          shellHook = ''
            echo "=================================================="
            echo " [⚡] J.E.S.U.S Arena: Java 25 (Cutting-Edge Core) "
            echo " Target System   : ${pkgs.stdenv.system}"
            echo " Java Version    : $(java -version 2>&1 | head -n 1)"
            echo "=================================================="
          '';
        };
      });

      # 2. 멀티OS / 멀티버전 배포 아티팩트 빌드 (Packages)
      packages = forEachSystem ({ pkgs }:
        let
          nativeVms = pkgs.stdenv.hostPlatform.isLinux;
          adaVm = pkgs.stdenv.mkDerivation {
            pname = "jesus-ada-vm";
            version = "1.0.0";
            src = ./ada-jvm;
            nativeBuildInputs = [ pkgs.gnat pkgs.gnatPackages.gprbuild ];
            buildPhase = ''
              runHook preBuild
              gprbuild -P ada_jvm.gpr
              runHook postBuild
            '';
            installPhase = ''
              runHook preInstall
              mkdir -p $out/bin
              cp build/bin/ada_jvm $out/bin/ada_jvm
              runHook postInstall
            '';
          };
          rustVm = pkgs.rustPlatform.buildRustPackage {
            pname = "jesus-rust-vm";
            version = "0.1.0";
            src = ./ada-jvm/rust-vm;
            cargoLock.lockFile = ./ada-jvm/rust-vm/Cargo.lock;
          };
          vmArguments = pkgs.lib.optionalAttrs nativeVms {
            inherit adaVm rustVm;
          };
        in
        {
          jesus-server-17 = buildJesusServer ({
            inherit pkgs;
            jdk = pkgs.jdk17;
            pname = "jesus-system-jdk17";
          } // vmArguments);
          jesus-server-25 = buildJesusServer ({
            inherit pkgs;
            jdk = pkgs.jdk25;
            pname = "jesus-system-jdk25";
          } // vmArguments);
        });
    };
}
