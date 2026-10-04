<div dir="rtl">

# DECISIONS

> הקובץ מסכם בקצרה את ההחלטות המרכזיות שקיבלתי במהלך המשימה.

## 1. החלטות ארכיטקטוניות

- העברתי את הלוגיקה העסקית מה־Controller ל־`LeaveRequestService`, כך שה־Controller אחראי בעיקר על HTTP וה־Service על חוקי המערכת.
- גישה לנתונים מתבצעת דרך ה־Repositories ולא ישירות מה־Controller.
- בצד ה־Frontend העברתי את קריאות ה־HTTP ל־`LeaveRequestService` והחלפתי שימוש ב־`any` ב־interfaces ו־enums מפורשים.
- בטופס יצירת בקשה המשתמש מזין תאריכי התחלה וסיום ולא מספר ימים. מספר הימים מחושב ב־Backend, כדי לשמור מקור אמת אחד ללוגיקה העסקית.

## 2. הבאג ביתרת החופשה

- מה היה הבאג, איפה, ואיך תיקנתי: ב־`LeaveRequestsController` חושב מספר ימי החופשה שכבר נוצלו (`used`), אך הערך לא השתתף בבדיקת המכסה. הבדיקה השוותה רק את מספר הימים של הבקשה החדשה ל־`annualQuota`. תיקנתי את התנאי כך שייבדק `used + days > annualQuota`.
- הטסט שמוכיח את התיקון: הוספתי טסט שבו לעובד יש מכסה של 20 ימים, 18 ימים כבר מאושרים, וניסיון ליצור בקשה נוספת של 3 ימים נדחה. הטסט גם מוודא שלא נשמרה בקשה חדשה במקרה זה.

## 3. אישור בקשה (approve) ו-concurrency

- איך טיפלתי במצבים לא חוקיים (כבר אושר / לא קיים): פעולת `approve` מתבצעת ב־Service בתוך transaction. בקשה שלא קיימת נדחית, וגם בקשה שאינה במצב `PENDING` אינה ניתנת לאישור מחדש. בנוסף, המכסה נבדקת שוב בזמן האישור ולא מסתמכת רק על המצב שהיה בזמן יצירת הבקשה.
- מה לגבי אישור של שתי בקשות במקביל: השתמשתי ב־`PESSIMISTIC_WRITE` locking. נעילה על הבקשה מגינה מפני אישור מקביל של אותה בקשה, ונעילה על העובד מסנכרנת אישורים של בקשות שונות עבור אותו עובד. לאחר קבלת הנעילה מחושבת מחדש המכסה הזמינה.
- הוספתי integration test שמריץ שני approvals במקביל כאשר נשאר מקום רק לאחד מהם. הטסט מוודא שרק אחד מצליח ושסך ימי החופשה המאושרים אינו עובר את המכסה.

## 4. על מה ויתרתי בגלל הזמן

- השארתי את מבנה ה־entities וה־API הקיים במקום לבצע שינוי רחב ל־DTOs עבור כל responses.
- הטיפול בשגיאות ב־Backend נשאר פשוט יחסית. עם עוד זמן הייתי מוסיף exceptions ייעודיים ו־`@RestControllerAdvice` עם HTTP status codes מדויקים יותר.
- עם עוד זמן הייתי מוסיף יותר בדיקות Frontend ובדיקות edge cases, וכן משפר את העיצוב וה־UX.
- הייתי מחדד את הגדרת מכסת החופשה לפי שנה, כולל החלטה מפורשת כיצד לטפל בבקשה שחוצה בין שתי שנים.

## 5. שימוש ב-AI

### איפה AI עזר (כולל prompts)

1. prompt: "Review the vacation quota validation and identify the bug" → AI עזר לזהות שהערך `used` מחושב אך לא משתתף בתנאי. בדקתי את הלוגיקה והוספתי regression test לפני/יחד עם התיקון.
2. prompt: "How should concurrent approval of leave requests be handled without exceeding an employee quota?" → AI הציע מספר אפשרויות ל־concurrency control. בחרתי transaction עם pessimistic locking ובדקתי את ההתנהגות באמצעות integration test מקבילי.
3. prompt: "Suggest a minimal Angular structure for form validation, approval state and typed HTTP access" → השתמשתי בהצעה כבסיס ל־service layer, טיפוסים, Reactive Forms ומצבי loading/error/success, והתאמתי אותה למבנה ול־API הקיימים בפרויקט.

### איפה דחיתי/תיקנתי הצעה של AI

- AI הציע בתחילה לכתוב שאילתת JPQL ידנית לחיפוש לפי שם עובד. לאחר בדיקה של ה־entity ראיתי שכבר קיימת relation מסוג `ManyToOne` בין `LeaveRequest` ל־`Employee`, ולכן השתמשתי ב־Spring Data derived query (`findByEmployee_NameContainingIgnoreCase`) במקום שאילתה ידנית.
- בנוסף, במהלך תכנון ה־concurrency זוהה שלא מספיק לקרוא מחדש entity דרך `findById` בתוך אותו persistence context, מכיוון ש־JPA עשוי להחזיר אותו מה־first-level cache. במקום זאת השתמשתי ב־pessimistic lock מפורש.

### אבטחה

- מצאתי SQL Injection בחיפוש בקשות לפי שם עובד: ה־Controller בנה native SQL באמצעות שרשור ישיר של הפרמטר `name`.
- החלפתי את ה־SQL הידני ב־Spring Data repository query המבוסס על ה־relation ל־`Employee`. הקלט מועבר כפרמטר ולא משורשר לתוך SQL, ולכן אינו יכול לשנות את מבנה השאילתה.
- בנוסף, העברתי את הגישה לנתונים מה־Controller ל־Service/Repository בהתאם לחלוקת האחריות.

## 6. הוראות הרצה

- הוראות ההרצה נשארו כפי שהוגדרו ב־README המקורי.

</div>
