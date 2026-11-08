rootProject.name = "Portmanteau"

// A test-only plugin for the live tests in test/live. It's never shipped.
include("live-test-helper")
project(":live-test-helper").projectDir = file("test/live/helper")
