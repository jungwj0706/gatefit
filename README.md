# GATEFIT

현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

GATEFIT은 내 차량의 제원과 시설의 출입구별 제한을 비교합니다. 사용자가 선택한 출입구의 좌표를 Pleos 기본 내비게이션에 전달해 시설의 대표 위치와 실제 진입 위치가 다른 경우를 돕습니다.

## APK 실행 방법

1. [서명된 제출용 APK](apk/%EC%A0%95%EC%9A%B0%EC%A7%84_GATEFIT_release.apk)를 열고 GitHub의 **Download raw file** 버튼으로 내려받으세요.
2. Pleos Connect 에뮬레이터를 실행한 뒤 APK를 내려받은 폴더에서 아래 명령을 실행하세요. `adb`는 Android SDK Platform-Tools에 포함되어 있습니다.

   ```bash
   adb install "./정우진_GATEFIT_release.apk"
   ```

3. 앱 목록에서 **GATEFIT**을 실행하세요.

APK의 대상 플랫폼은 Android Automotive OS입니다. 같은 패키지의 디버그 APK가 다른 키로 서명되어 설치된 환경에서는 새 테스트 환경을 사용하세요.

## 테스트 및 시연 환경

- 시연 영상은 일반 Android 15 에뮬레이터 36.5.10.0에서 1600×900 해상도로 녹화했습니다. 제출용 APK에서 차량 제원 등록, 시설 검색, 출입구 분석과 목적지 좌표 표시를 확인했습니다.
- Pleos Connect 에뮬레이터 버전과 화면 규격은 사용 기록이 없습니다. Pleos 환경의 Vehicle SDK 연결, NaviHelper 경로 탐색 및 콜백, Car UX 제한 동작은 미검증입니다.
- 내장 데이터로 시설 검색과 출입구 분석을 이용할 수 있으며, 별도 서버 설정이 필요하지 않습니다.

## 확인할 수 있는 흐름

1. **차량 등록**에서 모델을 선택하거나 치수를 직접 입력합니다. 루프박스 등 장착물을 추가하면 유효 제원에 반영됩니다.
2. **시설 검색**에서 `병원` 같은 검색어로 서울의 등록 시설을 찾습니다. 차량 출입구가 있는 시설을 선택합니다.
3. **출입구 분석·상세**에서 차량과 출입구의 제한 조건을 비교하고, 판정 근거와 출처를 확인합니다.
4. **목적지로 설정**이 활성화된 출입구를 선택해 Pleos 기본 내비게이션에 경로를 요청합니다. 요청 결과는 SDK 콜백을 기준으로 표시됩니다.

시설을 즐겨찾기에 저장하거나 출입구 정보 오류를 제보할 수 있습니다. 앱은 Car UX 제한 상태에 따라 검색·입력·제보 등 일부 동작을 제한합니다.

## 데이터와 이용 범위

서울 열린데이터광장의 시설물·차량 출입구 데이터가 내장돼 있어 서버 설정 없이 검색과 분석을 사용할 수 있습니다. 원본은 2022년 기준이며, 이 데이터만으로 적합 판정을 확정하지 않습니다. 실제 진입 전 현장 표지와 시설 운영 안내를 확인해 주세요. [데이터 출처 및 이용 조건](data/README.md)

설정에서 DEMO 데이터를 켜면 시연용 시설이 표시됩니다. DEMO 출입구는 경로 요청과 제보 대상에서 제외됩니다. 원격 서버가 구성되지 않은 경우 제보는 기기에 저장됩니다.

## 소스 빌드

- Gradle Wrapper 9.6.0, Android SDK Platform 36, Kotlin 2.2.10
- 앱 지원 범위 API 29 이상, 타깃 API 35
- Pleos Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5

Android Studio의 JDK와 Android SDK를 설정합니다. 명령줄에서 실행할 때는 `JAVA_HOME`을 설정하고, 저장소 루트의 `local.properties`에 `sdk.dir`을 지정합니다.

```bash
./gradlew :domain:test :app:test :app:assembleDebug
./gradlew :app:assembleRelease
```

`assembleRelease`가 생성하는 기본 APK는 서명되지 않습니다. 제출 APK는 별도 키로 서명했습니다. 자동 테스트는 도메인 판정, 데이터 계층, SDK 래퍼와 주요 ViewModel을 검증합니다.

별도 서버 구성은 [Supabase 설정 안내](supabase/README.md)를 참고하세요.
