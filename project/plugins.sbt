resolvers ++= Seq(
  "netlogo-publish-versioned" at "https://dl.cloudsmith.io/public/netlogo/publish-versioned/maven/"
)

addSbtPlugin("org.nlogo" % "publish-versioned-plugin" % "3.0.0")
