Electrical eGov Resilience

Free and Open-Source Research for Resilient Electrical Infrastructure

Free. Open. Verifiable. Resilient.

Our Mission

Electrical eGov Resilience is an open-source initiative dedicated to improving the security, reliability, and recoverability of critical electrical infrastructure.

Built upon the concepts of J.E.S.U.S (Java Ejectable Survival Unlimited Sustainable System), the project aims to combine Java-based system monitoring, eGovFrame integration, and electrical-grid resilience simulations.

Our mission is to make defensive infrastructure research freely accessible to developers, researchers, students, and institutions worldwide.

Core Principles

- Free and Open: Public source code and transparent research.
- Defense First: Prevention, monitoring, and recovery.
- Safety by Design: No direct control of operational power-grid equipment.
- Open Verification: Reproducible tests and independently reviewable results.
- Human Accountability: Safe recovery decisions with appropriate oversight.
- Global Collaboration: Contributions welcomed from around the world.

Technology Direction

- Java 17
- Spring and eGovFrame integration
- J.E.S.U.S-inspired system monitoring
- Microgrid outage simulation
- Backup-power endurance analysis
- Auditable recovery planning
- Automated testing and security review

Formal Verification

OpenJML specifications and static checking are used for the failure counter's safety properties. The CI workflow runs OpenJML ESC on `FailureCounter.java`, checking its non-negative invariant, saturating failure increment, recovery, threshold, and reset contracts. This currently verifies the failure counter only; it does not formally verify the Spring framework or the complete application.

Project Status

Early-stage development.

Development branch: "Dev"

Default branch: "main"

The software is not currently certified or ready for operational deployment. Compatibility and integration testing remain development objectives.

Proposed Electrical Resilience Foundation

Our long-term vision is to establish an independent, international, nonprofit open-source organization supporting research, education, verification, and collaboration in electrical infrastructure resilience.

The foundation has not yet been established.

Licensing

This project uses the MIT License. Commercial and noncommercial use are permitted under its terms. Copyright and license notices from upstream projects must be preserved.

AI Use and Disclosure

AI tools have been used to author parts of this repository's implementation. AI assistance is permitted for contributions; contributors are responsible for reviewing AI-assisted work and disclosing its use.

AI 도구가 이 리포지토리 구현의 일부를 작성하는 데 사용되었습니다. 기여 시 AI 사용이 허용되며, 기여자는 AI가 작성하거나 보조한 내용을 검토하고 AI 사용 사실을 밝혀야 합니다.

## Watchdog Application Documentation

For the Java watchdog's build and run instructions, activation requirements, safety notes, monitor behavior, and configuration reference, see [README_EN.md](README_EN.md).

## Experimental Ada JVM Project

The separate [`ada-jvm`](ada-jvm/README.md) project is an early bytecode-interpreter prototype, not a Java 25-compatible JVM.

## 한국어 안내

### 프로젝트 소개

Electrical eGov Resilience는 전력 인프라의 보안·신뢰성·복원력 향상을 연구하는 무료 오픈소스 프로젝트입니다. 현재 저장소에는 Java 17과 Spring Boot 기반의 J.E.S.U.S(Java Ejectable Survival Unlimited Sustainable System) 감시 프로토타입과 별도의 초기 Ada JVM 바이트코드 해석기 프로젝트가 포함되어 있습니다.

전력망 장애 분석, 모니터링, 검증 및 복구 지원을 위한 연구를 지향합니다. 실제 전력 설비를 직접 제어하거나 이에 대한 무단 접근을 수행하는 프로젝트가 아닙니다.

### 상태와 안전 안내

이 프로젝트는 초기 개발 단계이며, 운영 환경에 사용할 수 있도록 인증되거나 검증되지 않았습니다. 특히 Java 감시기는 설정에 따라 대상 Java 프로세스를 강제 종료하고 Nix로 재시작을 요청할 수 있습니다. 내장형 자동 구성은 기본적으로 비활성화되어 있지만, 활성화 전에 격리된 환경에서 프로세스 매칭과 복구 설정을 검증해야 합니다.

OpenJML 형식 검증은 현재 실패 횟수 카운터의 계약에만 적용됩니다. Spring 애플리케이션 전체나 운영 환경의 안전성을 증명하지 않습니다. Ada JVM 프로젝트 역시 완전한 JVM 또는 Java 호환 런타임이 아닙니다.

### 문서

- [Java 감시기 사용·설정·모니터 동작 안내 (영문)](README_EN.md)
- [Ada JVM 프로토타입 안내 (영문)](ada-jvm/README.md)
- [기여 안내](CONTRIBUTING.md)
- [보안 취약점 신고 및 보안 정책](SECURITY.md)
- [접근성 안내](ACCESSIBILITY.md)
- [감시기 HTTP API 안내 (영문)](docs/HTTP_API.md)
- [요구사항 추적표](docs/TRACEABILITY.md)

장기적으로 국제 개발자와 연구자가 함께 검증하고 협력하는 독립적인 비영리 오픈소스 재단으로 발전하는 것을 지향합니다. 재단은 아직 설립되지 않았습니다.
