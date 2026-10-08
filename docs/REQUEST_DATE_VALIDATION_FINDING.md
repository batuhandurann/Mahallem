# Request date validation finding

The request form currently defaults to a fixed date, 2026-10-18. The validator checks only for blank date and time fields, so it accepts past dates and invalid times. A fix should use a dynamic local date and strict date/time validation, with regression tests for emergency requests.
