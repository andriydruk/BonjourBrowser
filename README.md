# #StandWithUkraine
On Feb. 24, 2022 Russia declared an [unprovoked war on Ukraine](https://war.ukraine.ua/russia-war-crimes/) and launched a full-scale invasion. Russia is currently bombing peaceful Ukrainian cities, including schools and hospitals and attacking civilians who are fleeing conflict zones.

Please support Ukraine by lobbying your governments, protesting peacefully, and donating money to support the people of Ukraine. Below are links to trustworthy organizations that are helping to defend Ukraine in this unprovoked war:

* [Donate to Come Back Alive](https://www.comebackalive.in.ua/donate)
* [Donate to KOLO](https://koloua.com/en/)
* [Donate to Prytula Foundation](https://prytulafoundation.org/en)

# Service Browser

Android app for discovering and browsing mDNS/Bonjour services on local networks.

Discovers all services registered on the network, resolves their IP addresses, ports, and TXT records. Also supports registering your own services.

## Build

```bash
./gradlew assembleDebug          # Debug build
./gradlew assembleRelease        # Release build
./gradlew connectedAndroidTest   # Run instrumented tests
```

## Architecture

- **Language**: Kotlin
- **Pattern**: MVVM with ViewModels, Fragments, StateFlow/SharedFlow
- **Networking**: Android NsdManager for mDNS/DNS-SD discovery, resolution, and registration
- **Async**: Kotlin coroutines
- **UI**: Material Design 3 with dynamic colors, SlidingPaneLayout for tablet support
- **Min SDK**: 34 (Android 14) | **Target SDK**: 35 (Android 15) | **Compile SDK**: 36

## License

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
