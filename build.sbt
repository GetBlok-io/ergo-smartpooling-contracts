name := "ergo-smartpool-contracts"

version := "0.9"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "org.ergoplatform" %% "ergo-appkit" % "develop-d90135c5-SNAPSHOT",
  "org.slf4j" % "slf4j-jdk14" % "1.7.32",
  "org.postgresql" % "postgresql" % "42.3.1"
)

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)

assemblyJarName in assembly := s"ergo-smartpooling-${version.value}.jar"
mainClass in assembly := Some("app.SmartPoolingApp")
assemblyOutputPath in assembly := file(s"./ergo-smartpooling-${version.value}.jar/")