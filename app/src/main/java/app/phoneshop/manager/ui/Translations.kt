package app.phoneshop.manager.ui

/**
 * Sinhala and Tamil words for the app's labels, one line each: English|Sinhala|Tamil.
 * Anything not listed (long explanations, names, amounts) stays in English.
 * Sentences that start with a known label, like "Label: value", translate the label part.
 */
object Translations {
    private const val TABLE = """
Home|මුල් පිටුව|முகப்பு
Sell|විකුණන්න|விற்பனை
Sell (POS)|විකුණුම් (POS)|விற்பனை (POS)
Stock|තොග|சரக்கு
Money|මුදල්|பணம்
More|තවත්|மேலும்
Settings|සැකසුම්|அமைப்புகள்
Reports|වාර්තා|அறிக்கைகள்
Reports and admin|වාර්තා සහ පරිපාලනය|அறிக்கைகள் மற்றும் நிர்வாகம்
Products|නිෂ්පාදන|பொருட்கள்
Product|නිෂ්පාදනය|பொருள்
Customers|ගනුදෙනුකරුවන්|வாடிக்கையாளர்கள்
Customer|ගනුදෙනුකරු|வாடிக்கையாளர்
Suppliers|සැපයුම්කරුවන්|விநியோகஸ்தர்கள்
Supplier|සැපයුම්කරු|விநியோகஸ்தர்
Customers and suppliers|ගනුදෙනුකරුවන් සහ සැපයුම්කරුවන්|வாடிக்கையாளர்களும் விநியோகஸ்தர்களும்
Services|සේවා|சேவைகள்
Services and charges|සේවා සහ ගාස්තු|சேவைகளும் கட்டணங்களும்
Repairs|අලුත්වැඩියා|பழுதுபார்ப்பு
Bills|බිල්පත්|பில்கள்
Bill|බිල|பில்
Cart|කරත්තය|கூடை
Quick actions|ඉක්මන් ක්‍රියා|விரைவு செயல்கள்
Today|අද|இன்று
This week|මේ සතිය|இந்த வாரம்
This month|මේ මාසය|இந்த மாதம்
This year|මේ වසර|இந்த ஆண்டு
All|සියල්ල|அனைத்தும்
Everyone|සැමටම|அனைவரும்
Name|නම|பெயர்
Address|ලිපිනය|முகவரி
Phone / WhatsApp|දුරකථන / WhatsApp|தொலைபேசி / WhatsApp
Note|සටහන|குறிப்பு
Note (optional)|සටහන (අවශ්‍ය නම්)|குறிப்பு (விருப்பம்)
Save|සුරකින්න|சேமி
Cancel|අවලංගු කරන්න|ரத்து செய்
Delete|මකන්න|நீக்கு
Edit|සංස්කරණය|திருத்து
Done|අවසන්|முடிந்தது
Back|ආපසු|பின்செல்
Next|ඊළඟ|அடுத்து
Clear|මකන්න|அழி
Remove|ඉවත් කරන්න|அகற்று
Add item|අයිතමයක් එක් කරන්න|பொருளைச் சேர்
Add product|නිෂ්පාදනයක් එක් කරන්න|பொருள் சேர்
Add stock|තොග එක් කරන්න|சரக்கு சேர்
Add photo|ඡායාරූපයක් එක් කරන්න|புகைப்படம் சேர்
Change photo|ඡායාරූපය වෙනස් කරන්න|புகைப்படத்தை மாற்று
No photo|ඡායාරූපයක් නැත|புகைப்படம் இல்லை
Save product|නිෂ්පාදනය සුරකින්න|பொருளைச் சேமி
Edit product|නිෂ්පාදනය සංස්කරණය|பொருளைத் திருத்து
Delete product|නිෂ්පාදනය මකන්න|பொருளை நீக்கு
Category|වර්ගය|வகை
Categories and questions|වර්ග සහ ප්‍රශ්න|வகைகளும் கேள்விகளும்
Item code|අයිතම කේතය|பொருள் குறியீடு
Barcode|තීරු කේතය|பார்கோடு
Scan barcode|තීරු කේතය ස්කෑන් කරන්න|பார்கோடை ஸ்கேன் செய்
Scan barcode with camera|කැමරාවෙන් තීරු කේතය ස්කෑන් කරන්න|கேமராவால் பார்கோடை ஸ்கேன் செய்
Search name or item code|නම හෝ අයිතම කේතය සොයන්න|பெயர் அல்லது குறியீட்டைத் தேடு
Search name or phone|නම හෝ දුරකථනය සොයන්න|பெயர் அல்லது தொலைபேசியைத் தேடு
Search item, item code, IMEI or barcode|අයිතමය, කේතය, IMEI හෝ තීරු කේතය සොයන්න|பொருள், குறியீடு, IMEI அல்லது பார்கோடைத் தேடு
Search name, item code, barcode or IMEI|නම, කේතය, තීරු කේතය හෝ IMEI සොයන්න|பெயர், குறியீடு, பார்கோடு அல்லது IMEI தேடு
Low stock|තොග අඩුයි|சரக்கு குறைவு
Out of stock|තොග නැත|சரக்கு இல்லை
In stock|තොගයේ ඇත|கையிருப்பில்
Items in stock|තොගයේ අයිතම|கையிருப்பு பொருட்கள்
Stock value at cost|පිරිවැය අනුව තොග වටිනාකම|விலை மதிப்பில் சரக்கு
Stock snapshot|තොග සාරාංශය|சரக்கு சுருக்கம்
Stock adjustments|තොග සැකසුම්|சரக்கு திருத்தங்கள்
Sales today|අද විකුණුම්|இன்றைய விற்பனை
Profit today|අද ලාභය|இன்றைய லாபம்
All shop money|කඩයේ සියලු මුදල්|கடையின் மொத்தப் பணம்
New sale|නව විකුණුමක්|புதிய விற்பனை
New repair|නව අලුත්වැඩියාවක්|புதிய பழுது
View stock|තොග බලන්න|சரக்கைப் பார்
Quantity|ප්‍රමාණය|அளவு
How many|කීයද|எத்தனை
Total|එකතුව|மொத்தம்
Discount|වට්ටම|தள்ளுபடி
Includes tax|බදු ඇතුළුව|வரி உட்பட
Tax on bills|බිල්පත්වල බදු|பில்களில் வரி
Balance due|ගෙවිය යුතු ශේෂය|செலுத்த வேண்டிய மீதி
Fully paid|සම්පූර්ණයෙන් ගෙවා ඇත|முழுதும் செலுத்தப்பட்டது
Paid now|දැන් ගෙවූ මුදල|இப்போது செலுத்தியது
Payment now|දැන් ගෙවීම|இப்போதைய பணம்
Payment received|ලැබුණු ගෙවීම|பெற்ற பணம்
Take payment|ගෙවීම ගන්න|பணம் பெறு
Collect|ලබාගන්න|வசூல் செய்
Collect payment|ගෙවීම එකතු කරන්න|பணம் வசூலி
Finish sale|විකුණුම අවසන් කරන්න|விற்பனையை முடி
Start new sale|නව විකුණුමක් අරඹන්න|புதிய விற்பனை தொடங்கு
Share / print / save PDF|බෙදාගන්න / මුද්‍රණය / PDF සුරකින්න|பகிர் / அச்சிடு / PDF சேமி
Print on receipt printer|රිසිට්පත් මුද්‍රකයෙන් මුද්‍රණය කරන්න|ரசீது அச்சுப்பொறியில் அச்சிடு
Cancel this bill|මෙම බිල අවලංගු කරන්න|இந்த பில்லை ரத்து செய்
Cancelled|අවලංගු කළා|ரத்து செய்யப்பட்டது
Walk-in customer|සාමාන්‍ය ගනුදෙනුකරු|நேரடி வாடிக்கையாளர்
Hold|රඳවන්න|நிறுத்து வை
Quotation|මිල ගණන්|விலைப்புள்ளி
Quotations|මිල ගණන්|விலைப்புள்ளிகள்
Return items|භාණ්ඩ ආපසු දෙන්න|பொருட்களைத் திருப்பு
Refund|මුදල් ආපසු|பணத்திரும்பல்
Cash|මුදල්|ரொக்கம்
Bank|බැංකුව|வங்கி
Mobile wallet|ජංගම පසුම්බිය|மொபைல் வாலட்
Account|ගිණුම|கணக்கு
Accounts|ගිණුම්|கணக்குகள்
Money accounts|මුදල් ගිණුම්|பணக் கணக்குகள்
Transfer|මාරු කිරීම|பரிமாற்றம்
Transfers|මාරු කිරීම්|பரிமாற்றங்கள்
Expense|වියදම|செலவு
Expenses|වියදම්|செலவுகள்
Income|ආදායම|வருமானம்
Other income|වෙනත් ආදායම|பிற வருமானம்
Expenses and income|වියදම් සහ ආදායම්|செலவும் வருமானமும்
Expense and income types|වියදම් සහ ආදායම් වර්ග|செலவு, வருமான வகைகள்
Save expense|වියදම සුරකින්න|செலவைச் சேமி
Save income|ආදායම සුරකින්න|வருமானத்தைச் சேமி
Save transfer|මාරු කිරීම සුරකින්න|பரிமாற்றத்தைச் சேமி
Paid from|ගෙවූ ගිණුම|செலுத்திய கணக்கு
Paid into|ලැබූ ගිණුම|பெற்ற கணக்கு
From|සිට|இருந்து
To|දක්වා|வரை
Owes you|ඔබට ගෙවිය යුතුයි|உங்களுக்குக் கடன்
You owe them|ඔබ ගෙවිය යුතුයි|நீங்கள் தர வேண்டும்
Customers owe you|ගනුදෙනුකරුවන් ඔබට ගෙවිය යුතුයි|வாடிக்கையாளர்கள் உங்களுக்குத் தர வேண்டும்
You owe suppliers|ඔබ සැපයුම්කරුවන්ට ගෙවිය යුතුයි|நீங்கள் விநியோகஸ்தர்களுக்குத் தர வேண்டும்
Who owes what|කවුද කීයක් ගෙවිය යුත්තේ|யார் எவ்வளவு தர வேண்டும்
Opening balance|ආරම්භක ශේෂය|தொடக்க இருப்பு
New customer|නව ගනුදෙනුකරු|புதிய வாடிக்கையாளர்
New supplier|නව සැපයුම්කරු|புதிய விநியோகஸ்தர்
Customer profile|ගනුදෙනුකරු පැතිකඩ|வாடிக்கையாளர் விவரம்
Supplier profile|සැපයුම්කරු පැතිකඩ|விநியோகஸ்தர் விவரம்
Purchase orders|මිලදී ගැනීමේ ඇණවුම්|கொள்முதல் ஆர்டர்கள்
Purchases and supplier bills|මිලදී ගැනීම් සහ සැපයුම්කරු බිල්|கொள்முதலும் விநியோகஸ்தர் பில்களும்
Supplier bills|සැපයුම්කරු බිල්පත්|விநியோகஸ்தர் பில்கள்
Supplier bill|සැපයුම්කරු බිල|விநியோகஸ்தர் பில்
Receive goods|භාණ්ඩ ලබාගන්න|பொருட்களைப் பெறு
New purchase order|නව මිලදී ගැනීමේ ඇණවුම|புதிய கொள்முதல் ஆர்டர்
Send order|ඇණවුම යවන්න|ஆர்டரை அனுப்பு
Save draft|කෙටුම්පත සුරකින්න|வரைவைச் சேமி
Pay supplier|සැපයුම්කරුට ගෙවන්න|விநியோகஸ்தருக்குச் செலுத்து
Draft|කෙටුම්පත|வரைவு
Sent|යවා ඇත|அனுப்பப்பட்டது
Received|ලැබුණා|பெறப்பட்டது
Part received|කොටසක් ලැබුණා|பகுதி பெறப்பட்டது
Repair|අලුත්වැඩියාව|பழுது
New repair job|නව අලුත්වැඩියා කාර්යයක්|புதிய பழுது வேலை
Save job card|කාර්ය කාඩ්පත සුරකින්න|வேலை அட்டையைச் சேமி
Device|උපාංගය|சாதனம்
Estimate|ඇස්තමේන්තුව|மதிப்பீடு
Advance paid|ගෙවූ අත්තිකාරම|செலுத்திய முன்பணம்
Repair advance|අලුත්වැඩියා අත්තිකාරම|பழுது முன்பணம்
Ready for pickup|ගැනීමට සූදානම්|எடுக்கத் தயார்
Delivered|භාරදුන්නා|ஒப்படைக்கப்பட்டது
Repairing|අලුත්වැඩියා කරමින්|பழுதுபார்க்கப்படுகிறது
Waiting for parts|කොටස් බලා සිටී|பாகங்களுக்காகக் காத்திருக்கிறது
Deliver and take payment|භාරදී ගෙවීම ගන්න|ஒப்படைத்து பணம் பெறு
Warranty|වගකීම|உத்தரவாதம்
Warranty (months)|වගකීම (මාස)|உத்தரவாதம் (மாதங்கள்)
Problem in the customer's words|ගනුදෙනුකරුගේ වචනවලින් ගැටලුව|வாடிக்கையாளர் சொல்லும் பிரச்சினை
Parts used (taken from stock)|භාවිත කළ කොටස් (තොගයෙන්)|பயன்படுத்திய பாகங்கள் (சரக்கிலிருந்து)
Daily summary|දෛනික සාරාංශය|தினசரி சுருக்கம்
Monthly report|මාසික වාර්තාව|மாதாந்திர அறிக்கை
Profit and loss|ලාභ සහ අලාභ|இலாப நட்டம்
Net profit|ශුද්ධ ලාභය|நிகர லாபம்
Sales each day|දිනපතා විකුණුම්|தினசரி விற்பனை
Top items|ඉහළම අයිතම|சிறந்த பொருட்கள்
Best sellers|වැඩිපුරම විකුණන ඒවා|அதிகம் விற்பவை
Day close|දිනය වසා දැමීම|நாள் முடிவு
Day close (count the cash)|දිනය වසා දැමීම (මුදල් ගණන් කරන්න)|நாள் முடிவு (பணத்தை எண்ணு)
Close the day|දිනය වසන්න|நாளை முடி
Counted|ගණන් කළ|எண்ணியது
Cash in today|අද ලැබුණු මුදල්|இன்று வந்த பணம்
Cash out today|අද ගියපු මුදල්|இன்று சென்ற பணம்
Left in drawer|ලාච්චුවේ ඉතිරි|பெட்டியில் மீதம்
Reason|හේතුව|காரணம்
Reason (required)|හේතුව (අවශ්‍යයි)|காரணம் (அவசியம்)
Staff and roles|කාර්ය මණ්ඩලය සහ භූමිකා|ஊழியர்களும் பொறுப்புகளும்
Staff and activity log|කාර්ය මණ්ඩලය සහ ක්‍රියාකාරකම් සටහන|ஊழியர்களும் செயல்பாட்டுப் பதிவும்
Activity log|ක්‍රියාකාරකම් සටහන|செயல்பாட்டுப் பதிவு
New staff|නව සේවකයෙක්|புதிய ஊழியர்
Edit staff|සේවකයා සංස්කරණය|ஊழியரைத் திருத்து
Owner|හිමිකරු|உரிமையாளர்
Manager|කළමනාකරු|மேலாளர்
Cashier|අයකැමි|காசாளர்
People|පුද්ගලයින්|நபர்கள்
Who is using the app?|යෙදුම භාවිත කරන්නේ කවුද?|யார் பயன்படுத்துகிறார்கள்?
Pick your name and type your PIN.|ඔබේ නම තෝරා PIN ඇතුළත් කරන්න.|உங்கள் பெயரைத் தேர்ந்து PIN உள்ளிடுங்கள்.
Wrong PIN. Try again.|PIN වැරදියි. නැවත උත්සාහ කරන්න.|PIN தவறு. மீண்டும் முயலுங்கள்.
Open the shop|කඩය විවෘත කරන්න|கடையைத் திற
Use fingerprint|ඇඟිලි සලකුණ භාවිත කරන්න|கைரேகையைப் பயன்படுத்து
PIN lock|PIN අගුල|PIN பூட்டு
PIN lock for the owner|හිමිකරුගේ PIN අගුල|உரிமையாளருக்கான PIN பூட்டு
People and safety|පුද්ගලයින් සහ ආරක්ෂාව|நபர்களும் பாதுகாப்பும்
Fingerprint unlock for the owner|හිමිකරුට ඇඟිලි සලකුණු අගුළු ඇරීම|உரிமையாளருக்கு கைரேகை திறப்பு
Look|පෙනුම|தோற்றம்
Colour theme|වර්ණ තේමාව|வண்ணத் தீம்
Shop|කඩය|கடை
Shop details|කඩයේ විස්තර|கடை விவரங்கள்
Shop name, address and phone|කඩයේ නම, ලිපිනය සහ දුරකථනය|கடை பெயர், முகவரி, தொலைபேசி
Country and currency|රට සහ මුදල් ඒකකය|நாடும் நாணயமும்
Country|රට|நாடு
Currency|මුදල් ඒකකය|நாணயம்
Tax (GST / VAT)|බදු (GST / VAT)|வரி (GST / VAT)
Bill footer|බිල අග සටහන|பில் அடிக்குறிப்பு
Bill footer and printer|බිල් අග සටහන සහ මුද්‍රකය|பில் அடிக்குறிப்பும் அச்சுப்பொறியும்
Bill footer and receipt printer|බිල් අග සටහන සහ රිසිට් මුද්‍රකය|பில் அடிக்குறிப்பும் ரசீது அச்சுப்பொறியும்
Paper width|කඩදාසි පළල|காகித அகலம்
Bluetooth receipt printer|බ්ලූටූත් රිසිට් මුද්‍රකය|புளூடூத் ரசீது அச்சுப்பொறி
Find paired printers|යුගල කළ මුද්‍රක සොයන්න|இணைத்த அச்சுப்பொறிகளைத் தேடு
Print a test receipt|පරීක්ෂණ රිසිට්පතක් මුද්‍රණය කරන්න|சோதனை ரசீது அச்சிடு
Where your data lives|ඔබේ දත්ත ඇත්තේ කොහෙද|உங்கள் தரவு எங்குள்ளது
On this phone|මෙම දුරකථනයේ|இந்த தொலைபேசியில்
Always on|සැමවිටම ක්‍රියාත්මකයි|எப்போதும் இயக்கத்தில்
Back up now|දැන් උපස්ථ කරන්න|இப்போது காப்புப் பிரதி எடு
Back up now to a file|දැන් ගොනුවකට උපස්ථ කරන්න|இப்போது கோப்பில் காப்புப் பிரதி எடு
Restore|ප්‍රතිස්ථාපනය|மீட்டமை
Restore from a backup file|උපස්ථ ගොනුවකින් ප්‍රතිස්ථාපනය|காப்புக் கோப்பிலிருந்து மீட்டமை
Restore from online backup|ඔන්ලයින් උපස්ථයෙන් ප්‍රතිස්ථාපනය|ஆன்லைன் காப்பிலிருந்து மீட்டமை
Online backup|ඔන්ලයින් උපස්ථය|ஆன்லைன் காப்புப் பிரதி
Online backup (my Supabase)|ඔන්ලයින් උපස්ථය (මගේ Supabase)|ஆன்லைன் காப்பு (என் Supabase)
Save and test|සුරකින්න සහ පරීක්ෂා කරන්න|சேமித்து சோதி
Two or more phones|දුරකථන දෙකක් හෝ වැඩි|இரண்டு அல்லது அதிக தொலைபேசிகள்
Main phone|ප්‍රධාන දුරකථනය|முதன்மை தொலைபேசி
Viewer phone|බැලීමේ දුරකථනය|பார்வை தொலைபேசி
Off|අක්‍රියයි|நிறுத்தம்
On|සක්‍රියයි|இயக்கம்
Export all data (opens in Excel)|සියලු දත්ත ආනයනය (Excel හි විවෘත වේ)|அனைத்துத் தரவையும் ஏற்றுமதி செய் (Excel-இல் திறக்கும்)
Language|භාෂාව|மொழி
Setup script|සැකසුම් ස්ක්‍රිප්ට්|அமைவு ஸ்கிரிப்ட்
Set up your shop|ඔබේ කඩය සකසන්න|உங்கள் கடையை அமை
Please enter your shop name|කරුණාකර කඩයේ නම ඇතුළත් කරන්න|கடை பெயரை உள்ளிடுங்கள்
Your shop|ඔබේ කඩය|உங்கள் கடை
Brand|සන්නාමය|பிராண்ட்
Model|ආකෘතිය|மாடல்
Colour|වර්ණය|நிறம்
Storage|ආචයනය|சேமிப்பு
Battery|බැටරිය|பேட்டரி
Charging port|ආරෝපණ තොට|சார்ஜிங் போர்ட்
Screen replacement|තිරය මාරු කිරීම|திரை மாற்றம்
Software|මෘදුකාංග|மென்பொருள்
Other|වෙනත්|மற்றவை
Other work|වෙනත් වැඩ|பிற வேலைகள்
Prices (LKR)|මිල (LKR)|விலைகள் (LKR)
Selling|විකිණීම|விற்பனை
Buying cost|මිලදී ගැනීමේ පිරිවැය|வாங்கிய விலை
Buying stock|තොග මිලදී ගැනීම|சரக்கு வாங்குதல்
Cost each|එකක පිරිවැය|ஒன்றின் விலை
Average|සාමාන්‍ය|சராசரி
Details|විස්තර|விவரங்கள்
Required|අවශ්‍යයි|அவசியம்
Text|පෙළ|உரை
Number|අංකය|எண்
Yes / No|ඔව් / නැත|ஆம் / இல்லை
Kind|වර්ගය|வகை
Type|වර්ගය|வகை
Status (tap to move forward)|තත්ත්වය (ඉදිරියට යාමට ඔබන්න)|நிலை (முன்னேற்ற தொடு)
Nothing yet|තවම කිසිවක් නැත|இன்னும் எதுவும் இல்லை
Nothing here|මෙහි කිසිවක් නැත|இங்கே எதுவும் இல்லை
No products yet|තවම නිෂ්පාදන නැත|இன்னும் பொருட்கள் இல்லை
No bills yet|තවම බිල්පත් නැත|இன்னும் பில்கள் இல்லை
No orders yet|තවම ඇණවුම් නැත|இன்னும் ஆர்டர்கள் இல்லை
No jobs here|මෙහි කාර්යයන් නැත|இங்கே வேலைகள் இல்லை
No services yet|තවම සේවා නැත|இன்னும் சேவைகள் இல்லை
Nothing matches your search|ඔබේ සෙවුමට ගැළපෙන කිසිවක් නැත|உங்கள் தேடலுக்குப் பொருந்தவில்லை
New service|නව සේවාවක්|புதிய சேவை
Edit service|සේවාව සංස්කරණය|சேவையைத் திருத்து
New account|නව ගිණුමක්|புதிய கணக்கு
Edit account|ගිණුම සංස්කරණය|கணக்கைத் திருத்து
New category|නව වර්ගයක්|புதிய வகை
New question|නව ප්‍රශ්නයක්|புதிய கேள்வி
Money entry|මුදල් සටහන|பண பதிவு
New money entry|නව මුදල් සටහනක්|புதிய பணப் பதிவு
Money movement|මුදල් චලනය|பண இயக்கம்
See all money entries|සියලු මුදල් සටහන් බලන්න|அனைத்து பணப் பதிவுகளைப் பார்
All money entries|සියලු මුදල් සටහන්|அனைத்து பணப் பதிவுகள்
Owner put in|හිමිකරු දැමූ|உரிமையாளர் போட்டது
Owner took out|හිමිකරු ගත්|உரிமையாளர் எடுத்தது
I put money in|මම මුදල් දැමුවා|நான் பணம் போட்டேன்
I took money out|මම මුදල් ගත්තා|நான் பணம் எடுத்தேன்
Stock lost or damaged|අහිමි හෝ හානි වූ තොග|இழந்த அல்லது சேதமான சரக்கு
Damaged|හානි වූ|சேதமடைந்தது
Lost|අහිමි වූ|தொலைந்தது
Save adjustment|සැකසුම සුරකින්න|திருத்தத்தைச் சேமி
Remove stock|තොග ඉවත් කරන්න|சரக்கை அகற்று
Put back in stock|නැවත තොගයට දමන්න|மீண்டும் சரக்கில் சேர்
Add to stock|තොගයට එක් කරන්න|சரக்கில் சேர்
Save and add to stock|සුරකින්න සහ තොගයට එක් කරන්න|சேமித்து சரக்கில் சேர்
Choose a product.|නිෂ්පාදනයක් තෝරන්න.|ஒரு பொருளைத் தேர்ந்தெடுங்கள்.
Choose a supplier.|සැපයුම්කරුවෙකු තෝරන්න.|ஒரு விநியோகஸ்தரைத் தேர்ந்தெடுங்கள்.
Choose an account.|ගිණුමක් තෝරන්න.|ஒரு கணக்கைத் தேர்ந்தெடுங்கள்.
Enter the amount.|මුදල ඇතුළත් කරන්න.|தொகையை உள்ளிடுங்கள்.
Enter how many.|ප්‍රමාණය ඇතුළත් කරන්න.|எத்தனை என்று உள்ளிடுங்கள்.
Add at least one item.|අවම වශයෙන් එක් අයිතමයක් එක් කරන්න.|குறைந்தது ஒரு பொருளைச் சேர்க்கவும்.
Please enter a name.|කරුණාකර නමක් ඇතුළත් කරන්න.|பெயரை உள்ளிடுங்கள்.
Please enter the selling price.|කරුණාකර විකුණුම් මිල ඇතුළත් කරන්න.|விற்பனை விலையை உள்ளிடுங்கள்.
Backup saved.|උපස්ථය සුරකින ලදී.|காப்புப் பிரதி சேமிக்கப்பட்டது.
Online backup saved.|ඔන්ලයින් උපස්ථය සුරකින ලදී.|ஆன்லைன் காப்பு சேமிக்கப்பட்டது.
Connected. You can back up now.|සම්බන්ධයි. දැන් උපස්ථ කළ හැක.|இணைந்தது. இப்போது காப்பு எடுக்கலாம்.
Saved.|සුරකින ලදී.|சேமிக்கப்பட்டது.
Working...|වැඩ කරමින්...|செயல்படுகிறது...
Soon|ඉක්මනින්|விரைவில்
Not set|සකසා නැත|அமைக்கவில்லை
Never|කවදාවත් නැත|ஒருபோதும் இல்லை
Phone Shop Manager|ෆෝන් ෂොප් මැනේජර්|போன் ஷாப் மேனேஜர்
"""

    private val rows: Map<String, Pair<String, String>> by lazy {
        TABLE.lines().mapNotNull { l ->
            val p = l.split("|")
            if (p.size == 3) p[0] to (p[1] to p[2]) else null
        }.toMap()
    }

    /** Sinhala or Tamil for [text] when this language is on and the words are known. Otherwise the English text. */
    fun tr(text: String, lang: String): String {
        if (lang == "en" || text.isBlank()) return text
        fun one(s: String): String? = rows[s]?.let { if (lang == "si") it.first else it.second }
        one(text)?.let { return it }
        if (" · " in text) return text.split(" · ").joinToString(" · ") { one(it) ?: it }
        val i = text.indexOf(": ")
        if (i in 1..40) one(text.substring(0, i))?.let { return "$it: ${text.substring(i + 2)}" }
        return text
    }
}
