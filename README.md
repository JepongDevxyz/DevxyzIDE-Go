 <p align="center">
  <img src="./images/ADFA_logo.png" alt="Code On The Go" width="80" height="80"/>
</p>
<p align="center">
  💖 <strong>If you believe in our mission or just love our tools, please <a href="https://github.com/sponsors/appdevforall">sponsor our work</a>.</strong> 💖
</p>
<h2 align="center"><b>DevxyzIDE Go</b></h2>
<p align="center">
 DevxyzIDE Go is based on <b>Code on the Go</b> by <a href="https://github.com/appdevforall">App Dev for All</a>, the successor to <a href="https://github.com/AndroidIDEOfficial/AndroidIDE">AndroidIDE</a> by <a href="https://github.com/itsaky">Akash Yadav (itsaky)</a>. It is distributed under the GNU General Public License v3.0 (GPL-3.0) — see the License section below. </p>
<p align="center">
 Code on the Go is an IDE that lets you build Android apps on Android phones, without needing a traditional computer or Internet access. Code on the Go is a project of App Dev for All, a philanthropic venture focused on lowering the barriers to Android app development. </p>
<p><br>

<p align="center">
<img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License"></p>
<br>

<p align="center">
  <a href="https://github.com/appdevforall/CodeOnTheGo/issues">Report a bug or request a feature</a> &nbsp; &#8226; &nbsp;
   <a href="https://github.com/appdevforall/CodeOnTheGo/discussions">Support and discussions forum</a>&nbsp; &#8226; &nbsp;
   <a href="https://t.me/CodeOnTheGoOfficial">Telegram channel</a> &nbsp; &#8226; &nbsp;
<a href="https://github.com/appdevforall/CodeOnTheGo/wiki/Code-on-the-Go-Knowledge-Base">Knowledge base</a>
</p>

## Code on the Go and AndroidIDE

Code on the Go is the successor to [AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE), a popular mobile IDE that was archived in 2024. Code on the Go is actively maintained and updated weekly by App Dev for All. 

## Installation

Download the [Code on the Go APK](https://www.appdevforall.org/code-on-the-go?utm_source=gh&utm_medium=readme) from the [App Dev for All](https://www.appdevforall.org/?utm_source=gh&utm_medium=readme) website.


## Contributing

See the [contributing guide](./CONTRIBUTING.md).

## Thanks to

Code on the Go includes software developed by the [Apache Software Foundation](https://www.apache.org). Additional thanks to these developers:

- [Akash Yadav](https://github.com/itsaky) for [AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE)
- [Rosemoe](https://github.com/Rosemoe) for [CodeEditor](https://github.com/Rosemoe/sora-editor)
- [Termux](https://github.com/termux) for [Terminal Emulator](https://github.com/termux/termux-app)
- [Bogdan Melnychuk](https://github.com/bmelnychuk)
  for [AndroidTreeView](https://github.com/bmelnychuk/AndroidTreeView)
- [George Fraser](https://github.com/georgewfraser) for
  the [Java Language Server](https://github.com/georgewfraser/java-language-server)
- [Vivek](https://github.com/itsvks19) for [LayoutEditor](https://github.com/itsvks19/LayoutEditor)
 
Thanks to all the developers who have contributed to this project! 

## Contact us

- [Website](https://www.appdevforall.org)
- [Official Telegram channel](https://t.me/CodeOnTheGoOfficial)
- [Telegram discussions](https://t.me/CodeOnTheGoDiscussions)
- [Email](mailto:feedback@appdevforall.org)

## Documentation and reference materials
### Creative Commons
The following PDF is redistributed under the Creative Commons Attribution 4.0 International License (CC BY 4.0):
- Java, Java, Java: Object-Oriented Problem Solving, Third Edition by R. Morelli and R. Walde, previously published by Pearson Education, Inc. (Source: https://open.umn.edu/opentextbooks/textbooks/java-java-java-object-oriented-problem-solving)
  
### Free books from Goalkicker.com
- Android Notes for Professionals (Source: https://goalkicker.com/AndroidBook/)
- Java Notes for Professionals (Source: https://goalkicker.com/JavaBook/)
- Kotlin Notes for Professionals (Source: https://goalkicker.com/KotlinBook/)

### Third-Party Documentation
This project includes unmodified documentation from:

#### Android Mobile App Developer Tools
licensed under CC BY 4.0:
https://creativecommons.org/licenses/by/4.0/   
Source: https://developer.android.com

#### Kotlin Programming Language
licensed under CC BY 4.0:
https://creativecommons.org/licenses/by/4.0/   
Source: https://kotlinlang.org/

#### OpenJDK Development Kit (JDK) documentation
licensed under Debian Open Publications license: 
https://www.debian.org/opl   
Source: https://packages.debian.org/unstable/openjdk-17-doc


## License

```
Code On The Go is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Code On The Go is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Code On The Go.  If not, see <https://www.gnu.org/licenses/>.
```

Any violations to the license can be reported either by opening an issue or writing a mail to us
directly.



## DevxyzIDE Go build variant: downloadable toolchain assets

The CI release build on branch `devxyz-go-phase0` is made with `-PdevxyzDownloadAssets=true`: the APK does **not** bundle the large toolchain archives (Gradle distribution, Android SDK, bootstrap, local Maven repository, documentation DB, Gradle API jar, template core). On first launch, the IDE Setup downloads them from the upstream dev-assets mirror (`https://dev-assets.appdevforall.org/release/...`), verifying each file's MD5 against the mirror's `.md5` sidecar — the same archives and checksums the build itself uses. Downloads are cached in the app's files directory, so an interrupted setup resumes with only the missing or corrupt files. Building without the flag keeps the original fully-bundled APK. See `DownloadAssetsInstaller.kt`.
