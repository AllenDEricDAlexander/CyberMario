# Clocktower Base Script Rule Data

This directory stores the reviewed source data used to generate the Clocktower rule-data Flyway migration.

`clocktower-base-scripts-roles.csv` columns:

```csv
scriptCode,roleCode,name,roleType,alignment,abilityText,firstNightOrder,otherNightOrder,firstNightReminder,otherNightReminder,sourceUrl
```

`clocktower-base-scripts-night-order.csv` columns:

```csv
scriptCode,nightType,orderNo,roleCode,reminderText
```

Execution gate:

- The implementation must stop before creating `V20__complete_clocktower_rule_data.sql` if these files are missing
  complete reviewed data for all three base scripts.
- Role codes must use official enum names.
- Existing Flyway migrations must not be edited.
