# Changelog

## [8.0.0](https://github.com/Flagsmith/flagsmith-java-client/compare/v7.4.3...v8.0.0) (2025-11-26)


### ⚠ BREAKING CHANGES

* Context Values support, `getEvaluationResult` ([#184](https://github.com/Flagsmith/flagsmith-java-client/issues/184))

### Features

* Actualise context schema, `SPLIT` reason weight formatting ([#186](https://github.com/Flagsmith/flagsmith-java-client/issues/186)) ([b909755](https://github.com/Flagsmith/flagsmith-java-client/commit/b9097558fa83824068382457d944b721c206d01e))
* Context Values support, `getEvaluationResult` ([#184](https://github.com/Flagsmith/flagsmith-java-client/issues/184)) ([d892342](https://github.com/Flagsmith/flagsmith-java-client/commit/d89234228d379280831bf3a66d828c4580898c46))
* Send a standard `User-Agent: sdk-name/version` header ([#187](https://github.com/Flagsmith/flagsmith-java-client/issues/187)) ([8eab4ae](https://github.com/Flagsmith/flagsmith-java-client/commit/8eab4ae7829fec5c48e4c667abdacc6b12c23975))


### Bug Fixes

* `getEnvironmentFlags` includes segments in evaluation context ([#193](https://github.com/Flagsmith/flagsmith-java-client/issues/193)) ([9b55de9](https://github.com/Flagsmith/flagsmith-java-client/commit/9b55de940d2bb735125129ad0ed90f451493b8a8))
* Exclude identities when PERCENTAGE_SPLIT trait is undefined ([#198](https://github.com/Flagsmith/flagsmith-java-client/issues/198)) ([d1e2b59](https://github.com/Flagsmith/flagsmith-java-client/commit/d1e2b59ae7a7eaa1f3a6e9cea73b82c8b0938094))
* Multivariate sgement overrides not evaluated ([#194](https://github.com/Flagsmith/flagsmith-java-client/issues/194)) ([75749e6](https://github.com/Flagsmith/flagsmith-java-client/commit/75749e6f3fb3f9dc22b502a270452bb6daf1c2dc))


### CI

* Integrate release-please ([#188](https://github.com/Flagsmith/flagsmith-java-client/issues/188)) ([ddc2662](https://github.com/Flagsmith/flagsmith-java-client/commit/ddc2662f957d8629981a29153f1bf74951c4d0d6))
* update release please configuration to use simple release type ([#191](https://github.com/Flagsmith/flagsmith-java-client/issues/191)) ([7fc4fef](https://github.com/Flagsmith/flagsmith-java-client/commit/7fc4fefed1fe8929a0f0a88298768ef0dd06e0e9))


### Docs

* removing hero image and broken build badge from SDK readme ([#174](https://github.com/Flagsmith/flagsmith-java-client/issues/174)) ([49ee223](https://github.com/Flagsmith/flagsmith-java-client/commit/49ee223aa9b2fefcf1ed3bf2c22ef88e3a0eae9e))


### Other

* add root CODEOWNERS ([#181](https://github.com/Flagsmith/flagsmith-java-client/issues/181)) ([3e4f3e4](https://github.com/Flagsmith/flagsmith-java-client/commit/3e4f3e48c392aec6d392d704e6dfe66b15cd8729))
* versioned test data ([#178](https://github.com/Flagsmith/flagsmith-java-client/issues/178)) ([547afad](https://github.com/Flagsmith/flagsmith-java-client/commit/547afada20889ebaf9f0c352920dcea5a910119f))

<a name="v7.4.3"></a>
## [v7.4.3](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.4.3) - 09 Dec 2024

## What's Changed
* ci: publish workflow by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/169


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.4.2...v7.4.3

[Changes][v7.4.3]


<a name="v7.4.2"></a>
## [v7.4.2](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.4.2) - 13 Sep 2024

## What's Changed
* fix: Change visibilty of Protocol by [@madgaet](https://github.com/madgaet) in https://github.com/Flagsmith/flagsmith-java-client/pull/167

## New Contributors
* [@madgaet](https://github.com/madgaet) made their first contribution in https://github.com/Flagsmith/flagsmith-java-client/pull/167

**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.4.1...v7.4.2

[Changes][v7.4.2]


<a name="v7.4.1"></a>
## [v7.4.1](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.4.1) - 22 Aug 2024

## What's Changed
* fix: java 8 incompatibility by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/165


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.4.0...v7.4.1

[Changes][v7.4.1]


<a name="v7.4.0"></a>
## [v7.4.0](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.4.0) - 20 Aug 2024

## What's Changed
* fix: actions not running for PRs by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/138
* ci: remove code coverage by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/160
* ci: add checkstyle to workflows by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/161
* Add support for selection of protocols by [@olivier-hubaut](https://github.com/olivier-hubaut) in https://github.com/Flagsmith/flagsmith-java-client/pull/154
* fix: checkstyle violations by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/163
* feat: Support transient identities and traits by [@khvn26](https://github.com/khvn26) in https://github.com/Flagsmith/flagsmith-java-client/pull/158
* chore: bump version 7.4.0 by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/162
* chore: Remove merge leftovers by [@khvn26](https://github.com/khvn26) in https://github.com/Flagsmith/flagsmith-java-client/pull/164


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.3.0...v7.4.0

[Changes][v7.4.0]


<a name="v7.3.0"></a>
## [v7.3.0](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.3.0) - 03 Apr 2024

## What's Changed
* chore: remove examples by [@dabeeeenster](https://github.com/dabeeeenster) in https://github.com/Flagsmith/flagsmith-java-client/pull/148
* fix: Retry logic was never applied. by [@olivier-hubaut](https://github.com/olivier-hubaut) in https://github.com/Flagsmith/flagsmith-java-client/pull/146
* feat: Identity overrides in local evaluation mode by [@khvn26](https://github.com/khvn26) in https://github.com/Flagsmith/flagsmith-java-client/pull/142
* fix: thread safe analytics processor by [@Dogacel](https://github.com/Dogacel) in https://github.com/Flagsmith/flagsmith-java-client/pull/147
* Version bump 7.3.0 by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/150

## New Contributors
* [@olivier-hubaut](https://github.com/olivier-hubaut) made their first contribution in https://github.com/Flagsmith/flagsmith-java-client/pull/146
* [@Dogacel](https://github.com/Dogacel) made their first contribution in https://github.com/Flagsmith/flagsmith-java-client/pull/147

**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.2.0...v7.3.0

[Changes][v7.3.0]


<a name="v7.2.0"></a>
## [Version 7.2.0 (v7.2.0)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.2.0) - 24 Jan 2024

## What's Changed
* deps: bump okhttp by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/137
* chore: add callSuper=true to ToString method in Lombok config by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/140
* feat: add logic for offline mode in java by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/141


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.1.1...v7.2.0

[Changes][v7.2.0]


<a name="v7.1.1"></a>
## [Version 7.1.1 (v7.1.1)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.1.1) - 06 Dec 2023

## What's Changed
* fix: FlagsmithClient.close() doesn't kill polling manager properly by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/133


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.1.0...v7.1.1

[Changes][v7.1.1]


<a name="v7.1.0"></a>
## [Version 7.1.0 (v7.1.0)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.1.0) - 25 Jul 2023

## What's Changed
* feat: support `IN` operator for local evaluation by [@khvn26](https://github.com/khvn26) in https://github.com/Flagsmith/flagsmith-java-client/pull/128
* Bump okhttp3 by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/131

**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.0.1...v7.1.0

[Changes][v7.1.0]


<a name="v7.0.1"></a>
## [Version 7.0.1 (v7.0.1)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.0.1) - 14 Jul 2023

## What's Changed
* Fix dependency vulnerabilities by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/126
* Ensure that identity caches are separate by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/129
* Replace RuntimeException with FlagsmithRuntimeError by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/130


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.0.0...v7.0.1

[Changes][v7.0.1]


<a name="v7.0.0"></a>
## [Version 7.0.0 (v7.0.0)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v7.0.0) - 15 Jun 2023

## What's Changed
* **BREAKING CHANGE**: fix: consistent split evaluations by [@khvn26](https://github.com/khvn26) in https://github.com/Flagsmith/flagsmith-java-client/pull/122
* Bump guava from 30.1-jre to 32.0.0-jre by [@dependabot](https://github.com/dependabot) in https://github.com/Flagsmith/flagsmith-java-client/pull/123

**WARNING**: We modified the local evaluation behaviour. You may see different flags returned to identities attributed to your percentage split-based segments after upgrading to this version.

## New Contributors
* [@khvn26](https://github.com/khvn26) made their first contribution in https://github.com/Flagsmith/flagsmith-java-client/pull/122

**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v6.2.0...v7.0.0

[Changes][v7.0.0]


<a name="v6.2.0"></a>
## [Version 6.2.0 (v6.2.0)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v6.2.0) - 15 Jun 2023

## What's Changed
* remove lombok [@data](https://github.com/data) causing StackOverflowError issue with springboot by [@p-maks](https://github.com/p-maks) in https://github.com/Flagsmith/flagsmith-java-client/pull/119

**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v6.1.0...v6.2.0

[Changes][v6.2.0]


<a name="v6.1.0"></a>
## [Version 6.1.0 (v6.1.0)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v6.1.0) - 03 Apr 2023

## What's Changed
* Replace TestNG with JUnit5 by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/117


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v6.0.1...v6.1.0

[Changes][v6.1.0]


<a name="v6.0.1"></a>
## [Version 6.0.1 (v6.0.1)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v6.0.1) - 29 Mar 2023

## What's Changed
* Fix Sonatype Jackson vulnerability by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/113

**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v6.0.0...v6.0.1

[Changes][v6.0.1]


<a name="v6.0.0"></a>
## [Version 6.0.0 (v6.0.0)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v6.0.0) - 29 Mar 2023

## What's Changed
* Fix tests expecting the wrong exception by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/108
* chore/codeql bump by [@dabeeeenster](https://github.com/dabeeeenster) in https://github.com/Flagsmith/flagsmith-java-client/pull/110
* Bump jackson-databind from 2.13.3 to 2.13.5 by [@dependabot](https://github.com/dependabot) in https://github.com/Flagsmith/flagsmith-java-client/pull/92
* Bump testng from 6.14.3 to 7.7.0 by [@dependabot](https://github.com/dependabot) in https://github.com/Flagsmith/flagsmith-java-client/pull/111
* Prevent duplicate environment updates on polling manager start by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/112
* Use default flags in local evaluation mode by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/107
* Release 6.0 by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/109

## New Contributors
* [@dependabot](https://github.com/dependabot) made their first contribution in https://github.com/Flagsmith/flagsmith-java-client/pull/92

## Breaking changes

* Clients in local evaluation mode will no longer throw `RuntimeException` if unable to retrieve environment on startup and default handler is provided
* `enableEnvLevelCaching` will now throw `IllegalArgumentException` for null key
* `identifyUserWithTraits` will now throw `IllegalArgumentException` for null identifier
* Target Java version raised to 11

**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.1.2...v6.0.0

[Changes][v6.0.0]


<a name="v5.1.2"></a>
## [Version 5.1.2 (v5.1.2)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v5.1.2) - 20 Jan 2023

## What's Changed
* fix/is-feature-enabled-nullpointer: Remove the possibility of a null … by [@ajhelsby](https://github.com/ajhelsby) in https://github.com/Flagsmith/flagsmith-java-client/pull/104
* Fix return types from `getFeatureValue` by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/103
* Release 5.1.2 by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/102

## New Contributors
* [@ajhelsby](https://github.com/ajhelsby) made their first contribution in https://github.com/Flagsmith/flagsmith-java-client/pull/104

**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.1.1...v5.1.2

[Changes][v5.1.2]


<a name="v5.1.1"></a>
## [Version 5.1.1 (v5.1.1)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v5.1.1) - 04 Nov 2022

## What's Changed
* Fix NPE in identifyUserWithTraits when using caching  by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/100
* Ensure that environment is updated on polling manager start by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/99


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.1.0...v5.1.1

[Changes][v5.1.1]


<a name="v5.1.0"></a>
## [Version 5.1.0 (v5.1.0)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v5.1.0) - 01 Nov 2022

## What's Changed
* Add IS_SET and IS_NOT_SET operators by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/90
* Implement modulo operator by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/91
* Add ability to include an optional proxy on the Http Client by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/94
* Release 5.1.0 by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/89
* Fix semver javadoc by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/95


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.0.4...v5.1.0

[Changes][v5.1.0]


<a name="v5.0.4"></a>
## [Version 5.0.4 (v5.0.4)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v5.0.4) - 24 Aug 2022

## What's Changed
* Handle exceptions in polling manager by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/79


**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.0.3...v5.0.4

[Changes][v5.0.4]


<a name="v5.0.0"></a>
## [Version 5.0.0 (v5.0.0)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v5.0.0) - 07 Jun 2022

## What's Changed
* Flag Engine Implementation by [@fzia](https://github.com/fzia) in https://github.com/Flagsmith/flagsmith-java-client/pull/53
* Rewrite for client side evaluation by [@fzia](https://github.com/fzia) in https://github.com/Flagsmith/flagsmith-java-client/pull/55
* Identity Segments call exposed by [@fzia](https://github.com/fzia) in https://github.com/Flagsmith/flagsmith-java-client/pull/63
* prevent initialization with out server key for local evaluation by [@fzia](https://github.com/fzia) in https://github.com/Flagsmith/flagsmith-java-client/pull/64
* Fix segment priorities by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/67
* Update default api url to point to edge by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/68
* Semver Support - Added tests and support for Semantic Versions by [@fzia](https://github.com/fzia) in https://github.com/Flagsmith/flagsmith-java-client/pull/65
* Release version 5.0.0 by [@matthewelwell](https://github.com/matthewelwell) in https://github.com/Flagsmith/flagsmith-java-client/pull/54

## New Contributors
* [@fzia](https://github.com/fzia) made their first contribution in https://github.com/Flagsmith/flagsmith-java-client/pull/53

**Full Changelog**: https://github.com/Flagsmith/flagsmith-java-client/compare/v4.0.2...v5.0.0

[Changes][v5.0.0]


<a name="v1.5.0"></a>
## [Full ConfigBuilder support (v1.5.0)](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v1.5.0) - 11 Jun 2020

You can now override all config options with the builder. 

[Changes][v1.5.0]


<a name="v1.3.0"></a>
## [v1.3.0](https://github.com/Flagsmith/flagsmith-java-client/releases/tag/v1.3.0) - 11 Jan 2019

added UserTraits feature support

[Changes][v1.3.0]


[v7.4.3]: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.4.2...v7.4.3
[v7.4.2]: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.4.1...v7.4.2
[v7.4.1]: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.4.0...v7.4.1
[v7.4.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.3.0...v7.4.0
[v7.3.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.2.0...v7.3.0
[v7.2.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.1.1...v7.2.0
[v7.1.1]: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.1.0...v7.1.1
[v7.1.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.0.1...v7.1.0
[v7.0.1]: https://github.com/Flagsmith/flagsmith-java-client/compare/v7.0.0...v7.0.1
[v7.0.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v6.2.0...v7.0.0
[v6.2.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v6.1.0...v6.2.0
[v6.1.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v6.0.1...v6.1.0
[v6.0.1]: https://github.com/Flagsmith/flagsmith-java-client/compare/v6.0.0...v6.0.1
[v6.0.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.1.2...v6.0.0
[v5.1.2]: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.1.1...v5.1.2
[v5.1.1]: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.1.0...v5.1.1
[v5.1.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.0.4...v5.1.0
[v5.0.4]: https://github.com/Flagsmith/flagsmith-java-client/compare/v5.0.0...v5.0.4
[v5.0.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v1.5.0...v5.0.0
[v1.5.0]: https://github.com/Flagsmith/flagsmith-java-client/compare/v1.3.0...v1.5.0
[v1.3.0]: https://github.com/Flagsmith/flagsmith-java-client/tree/v1.3.0

<!-- Generated by https://github.com/rhysd/changelog-from-release v3.7.2 -->
