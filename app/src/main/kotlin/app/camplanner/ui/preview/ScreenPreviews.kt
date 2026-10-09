package app.camplanner.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.camplanner.designsystem.theme.CamPlannerTheme
import app.camplanner.ui.checkin.CheckInScreen
import app.camplanner.ui.fitness.FitnessScreen
import app.camplanner.ui.today.TodayScreen

private const val INK = 0xFF0B0D14

@Preview(name = "Today", widthDp = 393, heightDp = 960, showBackground = true, backgroundColor = INK)
@Composable
fun TodayPreview() = CamPlannerTheme {
    TodayScreen(SampleData.today, onRoute = {}, onOpenEvent = {}, onOpenCheckIn = {}, onFixPermissions = {})
}

@Preview(name = "Today, permission missing", widthDp = 393, heightDp = 400, showBackground = true, backgroundColor = INK)
@Composable
fun TodayAttentionPreview() = CamPlannerTheme {
    TodayScreen(
        SampleData.today.copy(attention = "Alarms are off: allow exact alarms"),
        onRoute = {}, onOpenEvent = {}, onOpenCheckIn = {}, onFixPermissions = {},
    )
}

@Preview(name = "Daily check-in", widthDp = 393, heightDp = 1180, showBackground = true, backgroundColor = INK)
@Composable
fun CheckInPreview() = CamPlannerTheme {
    CheckInScreen(SampleData.checkIn, onPagesChange = { _, _ -> }, onHomeworkDone = { _, _ -> }, onLogRemaining = {}, onSave = {})
}

@Preview(name = "Fitness", widthDp = 393, heightDp = 1250, showBackground = true, backgroundColor = INK)
@Composable
fun FitnessPreview() = CamPlannerTheme {
    FitnessScreen(
        SampleData.fitness,
        onSelectMain = {}, onQuickAdd = { _, _ -> }, onCustomAmountChange = {}, onAddCustom = {},
        onLogRun = {}, onOpenHistory = {}, onManageExercises = {},
    )
}
