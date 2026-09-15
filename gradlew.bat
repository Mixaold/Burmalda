@rem
@rem Standard Gradle wrapper launcher (restored).
@rem Uses gradle/wrapper/gradle-wrapper.jar so gradle-wrapper.properties controls the Gradle version.
@rem
@if "%DEBUG%"=="" @echo off
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

@rem Fall back to a known JDK if JAVA_HOME is not provided by the caller.
if "%JAVA_HOME%"=="" set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot

set JAVA_EXE=%JAVA_HOME%\bin\java.exe
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

set EXIT_CODE=%ERRORLEVEL%
if "%OS%"=="Windows_NT" endlocal
exit /b %EXIT_CODE%
