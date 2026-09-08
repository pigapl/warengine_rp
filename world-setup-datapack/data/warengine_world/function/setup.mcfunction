# Runs on every world load (hooked to #minecraft:load).
# Event maps are short daytime fights, and a fresh map every event would otherwise mean
# retyping these by hand each time.

gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doMobSpawning false

time set noon
weather clear
