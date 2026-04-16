# Implementation Plan: Comprehensive Activity Metrics & UI Refactoring

This plan outlines the steps to introduce new activity statistics ("Attività giornaliera", "Attività mensile", "Attività annuale") and refactor the UI to use a Tab Switcher for a cleaner and more professional overview.

## User Review Required

> [!IMPORTANT]
> - **Performance**: Fetching 5 different distribution sets might slightly increase load times. I will optimize the DAO queries to run efficiently.
> - **UI Change**: The current single-view Activity card will be replaced by a Tabbed interface.
> - **Chart Scrolling**: For the 31-day and 1-12 month views, the charts will support horizontal scrolling to ensure readability on smaller screens.

## Proposed Changes

### 1. Data Layer (Database & Models)

#### [MODIFY] [QueryModels.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/data/db/model/QueryModels.kt)
- Add generic `GenericCount` data class to handle different time groupings (label: String, count: Int).
- Or add specific `DayOfMonthCount`, `MonthOfYearCount`, and `YearStat` classes if needed for clarity.

#### [MODIFY] [MessageDao.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/data/db/dao/MessageDao.kt)
- Add `getGlobalDayOfMonthDistribution(startTime, endTime)`: Group by `%d` (1-31).
- Add `getGlobalMonthOfYearDistribution(startTime, endTime)`: Group by `%m` (1-12).
- Add `getGlobalYearTrendDistribution(startTime, endTime)`: Group by `%Y` (Years).
- Implement the same for specific chat IDs.

### 2. Domain & Repository Layer

#### [MODIFY] [Stats.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/domain/model/Stats.kt)
- Update `ChatStats` and `GlobalStats` to include the new distribution fields.

#### [MODIFY] [AnalyticsRepository.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/data/repository/AnalyticsRepository.kt)
- Expose the new distribution methods.
- Update `getGlobalStats` and `getQuickChatStats` to fetch the new data points.

### 3. ViewModel Layer

#### [MODIFY] [DashboardViewModel.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/ui/screen/dashboard/DashboardViewModel.kt)
- Update to fetch and expose all 5 distribution types.

#### [MODIFY] [ChatDetailViewModel.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/ui/screen/chatdetail/ChatDetailViewModel.kt)
- Update to fetch all 5 distribution types per chat.

### 4. UI Components

#### [MODIFY] [InteractiveChart.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/ui/component/InteractiveChart.kt)
- Create a generic `InteractiveStatChart` component.
- Supports horizontal scrolling for high-density data (like 31 days).
- Supports different label types.

#### [NEW] [ActivityCard.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/ui/component/ActivityCard.kt)
- A specialized component that wraps the `TabRow` and the selected chart view.

### 5. Screen Integration

#### [MODIFY] [DashboardScreen.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/ui/screen/dashboard/DashboardScreen.kt)
- Update the "Attività oraria" card to the new tabbed `ActivityCard`.

#### [MODIFY] [ChatDetailScreen.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/ui/screen/chatdetail/ChatDetailScreen.kt)
- Update the "Attività oraria" card to the new tabbed `ActivityCard`.

---

## Verification Plan

### Automated Tests
- N/A (UI and DB integration testing)

### Manual Verification
- Check Dashboard for the 5-tab switcher.
- Verify that filtering by date updates all charts as expected.
- Verify that horizontal scrolling works on the "Attività giornaliera" (31 elements).
- Confirm that "Attività annuale" correctly shows years (2022, 2023...).
