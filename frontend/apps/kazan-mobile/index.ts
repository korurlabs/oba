// The push background task is defined before expo-router loads: Android may
// start the app headless only to run that task, and a task defined later, in
// a layout, would not exist when the system calls it.
import "./lib/pushTask";
import "expo-router/entry";
