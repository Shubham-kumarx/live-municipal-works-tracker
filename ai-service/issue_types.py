CLASS_LABELS = (
    "DOMESTIC_TRASH",
    "ILLEGAL_PARKING",
    "DAMAGED_SIGN",
    "POTHOLE",
)

CLASS_TO_INDEX = {label: index for index, label in enumerate(CLASS_LABELS)}
INDEX_TO_CLASS = {index: label for index, label in enumerate(CLASS_LABELS)}

DATASET_FOLDER_TO_ISSUE_TYPE = dict(zip((
    "Domestic_trash",
    "Parking_Issues_Illegal_Parking",
    "Road_Issues_Damaged_Sign",
    "Road_Issues_Pothole",
), CLASS_LABELS, strict=True))

EXCLUDED_DATASET_FOLDERS = frozenset({
    "Infrastructure_Damage_Concrete",
    "Vandalism_Graffiti",
})

SUPPORTED_ISSUE_TYPES = CLASS_LABELS

ISSUE_CATEGORIES = {
    "DOMESTIC_TRASH": "SANITATION",
    "ILLEGAL_PARKING": "ROAD",
    "DAMAGED_SIGN": "ROAD",
    "POTHOLE": "ROAD",
}
