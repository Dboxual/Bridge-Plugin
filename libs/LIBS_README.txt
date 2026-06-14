# Bridge-Plugin — Required Build Dependencies

Place the following jars in this directory before running build.sh:

  paper-api.jar           Paper API 1.21.x (from https://repo.papermc.io)
  fawe-bukkit.jar         FastAsyncWorldEdit (from https://ci.athion.net/job/FastAsyncWorldEdit/)
  adventure-api.jar       net.kyori:adventure-api (bundled with Paper)
  adventure-key.jar       net.kyori:adventure-key (bundled with Paper)
  jetbrains-annotations.jar  org.jetbrains:annotations
  guava.jar               com.google.guava:guava (bundled with Paper)
  examination-api.jar     net.kyori:examination-api (bundled with Paper)
  bungeecord-chat.jar     net.md-5:bungeecord-chat (bundled with Paper)

Note: FastAsyncWorldEdit-Paper-2.15.0.jar was previously tracked in the repo
root — this was a mistake. Use fawe-bukkit.jar renamed to fawe-bukkit.jar in
this libs/ directory. Download the matching FAWE version from the link above.

All jars in libs/ are gitignored. See build.sh for classpath usage.
