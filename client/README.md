MedicalDocAI

MedicalDocAI is an Android application designed to interact with a backend AI server for processing medical documents. This repository contains the Android client source code.

Prerequisites

Before building or running the application, ensure you have the following installed:

Java Development Kit (JDK): Version 11 or higher. Check with java -version.

Android SDK:

Android Studio Users: Included automatically.

VS Code / CLI Users: Command line tools only.

Server Backend: You must have the MedicalDocAI backend server running on a machine accessible via Wi-Fi.

Configuration

To connect the Android app to your local backend server, you must configure the API URL before building.

Find your Server IP:

Open a terminal on your backend server PC.

Run ip addr (Linux/Mac) or ipconfig (Windows).

Locate the IPv4 address for your Wi-Fi adapter (e.g., 192.168.1.x).

Ensure both your PC and Android device are on the same Wi-Fi network.

Update gradle.properties:

Open gradle.properties in the root of this project.

Locate the SERVER_URL line at the bottom.

Update it with your IP address:

SERVER_URL=http://YOUR_IP_ADDRESS:8000/


Note: Keep the protocol (http://), port (:8000), and trailing slash (/).

Build Instructions

You can build the application using either the command line (recommended for VS Code users) or Android Studio.

Option A: Command Line / VS Code

Open a terminal in the project root.

(Mac/Linux only) Make the wrapper executable:

chmod +x gradlew


Build the debug APK:

Mac/Linux:

./gradlew assembleDebug


Windows:

.\gradlew.bat assembleDebug


Option B: Android Studio

Open the project in Android Studio.

Allow Gradle to sync.

Navigate to Build > Build Bundle(s) / APK(s) > Build APK(s).

Installation

After a successful build, locate the APK file:

app/build/outputs/apk/debug/app-debug.apk


Transfer this file to your Android device.

Install the APK.

Ensure the backend server is running and your phone is connected to the same Wi-Fi network.

Launch MedicalDocAI.
