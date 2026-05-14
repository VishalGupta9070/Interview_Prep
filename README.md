## InterviewPrepAI (UI-only scaffold)

This project recreates the Stitch-designed UI in **Jetpack Compose Material 3** with **MVVM + Clean Architecture** structure and **Navigation Compose**.

### What’s included
- **Screens**: Splash, Home Dashboard, Resume Upload, Q&A, Mock Interview Chat, Feedback
- **Reusable composables**: `FeatureCard`, `QuestionCard` (expandable), `ChatBubble` (AI/User), `ChatInputField`, `PrimaryButton`, `LoadingIndicator`
- **Architecture**: `domain/`, `data/`, `viewmodel/`, `ui/` with `StateFlow`-based UI state
- **Data**: Dummy/static repositories (`FakeFeatureRepository`, `FakeInterviewRepository`)

### Run
- Open the project folder `InterviewPrepAI/` in Android Studio
- Ensure you have **JDK 17** installed and configured
- Build/Run the `app` configuration

### Pixel-perfect note (Stitch)
The provided Stitch URL currently appears to require authentication from this environment, so the project ships with **placeholder theme tokens and icons**.
Once you export/share the Stitch design tokens (colors/typography/spacing) or provide screen exports, we can replace `ui/theme/*` and the placeholder icons to match the design **exactly**.

