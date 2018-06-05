homepage := Some(url("https://github.com/SolidStateGroup/bullet-train-java-client"))

scmInfo := Some(ScmInfo(url("https://github.com/SolidStateGroup/bullet-train-java-client")
                            "git@github.com:SolidStateGroup/bullet-train-java-client.git"))
developers := List(Developer("p-maks",
                             "Pavlo Maks",
                             "pavlo.maksymchuk@gmail.com",
                             url("https://github.com/p-maks")))
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
publishMavenStyle := true

// Add sonatype repository settings
publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)