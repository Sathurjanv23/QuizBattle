# Pull Request Review Checklist: Foundation Architecture

This checklist is used to review and approve **PR 00: QuizBattle project foundation**.

### Preferred Reviewer: **Member 2**

---

## 🎯 Architecture & Scope
- [ ] No fake or mock implementations masquerading as working game logic.
- [ ] Clean interfaces: `QuizGameService`, `QuizRoomService`, `MessageCodec`, `NetworkClient`, `ServerMessageDispatcher`, `ClientSession`.
- [ ] Package structure adheres strictly to the 13 designated packages under `com.quizbattle.*`.
- [ ] No circular package dependencies.

## 📡 Protocol & Framing
- [ ] UTF-8 newline-delimited (`\n`) JSON framing enforced in `JsonMessageCodec`.
- [ ] Buffer cap (64 KiB) enforced with `ProtocolException` when exceeded.
- [ ] Malformed JSON handled gracefully without crashing server.
- [ ] Central security rule documented: client NEVER supplies trusted score or `playerId`; server session holds authorization context.
- [ ] Default ports recorded: TCP `5000`, UDP Discovery `5001`.

## 💻 JavaFX Client & Thread Safety
- [ ] Navigation container (`NavigationContainer`) provides root layout and back-stack navigation.
- [ ] All network and background callbacks route through `UiCallbackDispatcher` (`Platform.runLater()`).
- [ ] Modern dark theme CSS loaded from `resources/styles/app.css`.
- [ ] Standalone `ClientLauncher` allows execution without VM module-path arguments.

## 🛠️ Build & CI
- [ ] `pom.xml` configures Java 21, JavaFX 21, Jackson, JUnit 5, and Logback.
- [ ] `mvn -B clean test` passes on Windows and Linux.
- [ ] GitHub Actions workflow (`.github/workflows/maven.yml`) tests matrix on Ubuntu and Windows.
- [ ] `.gitignore` properly excludes `target/`, IDE files, OS files, and secrets.
- [ ] Member 1 handover steps are clear and actionable in `docs/HANDOVER.md`.
