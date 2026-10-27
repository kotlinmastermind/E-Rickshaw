import asyncio
import os
import sys
from dotenv import load_dotenv

sys.path.insert(0, r"c:\androidAi")
load_dotenv(r"c:\androidAi\.env")

from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy import text, select
from backend.domain.models import TourismPlace
from backend.infrastructure.database.session import Base

db_url = os.getenv("DATABASE_URL")
if not db_url:
    print("DATABASE_URL not found!")
    sys.exit(1)

PLACES_DATA = [
    # Group 0 (Week 1 / Rotation A)
    {
        "title": "Vaishali Ashoka Pillar & Buddha Relic Stupa",
        "category": "HISTORICAL",
        "short_description": "Ancient capital of the Licchavis. Features Emperor Ashoka's monolithic lion pillar from 3rd century BCE and the sacred Buddha relic stupa.",
        "photo_url": "https://images.unsplash.com/photo-1590050752117-238cb0fb12b1?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.9922,
        "longitude": 85.1264,
        "address": "Kolhua, Vaishali, Bihar 844128",
        "city": "Vaishali",
        "rating": 4.8,
        "review_count": 2450,
        "entry_fee": "₹25 (ASI Monument)",
        "timings": "06:00 AM – 06:00 PM",
        "tag": "Ashokan Pillar",
        "week_rotation_group": 0,
        "is_featured": True
    },
    {
        "title": "Mahabodhi Temple & Sacred Bodhi Tree",
        "category": "SPIRITUAL_HERITAGE",
        "short_description": "UNESCO World Heritage Site where Lord Buddha attained enlightenment in 531 BCE. Revered 50-meter ancient stone temple spire.",
        "photo_url": "https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=800&q=80",
        "latitude": 24.6958,
        "longitude": 84.9914,
        "address": "Bodh Gaya, Gaya, Bihar 824231",
        "city": "Bodh Gaya",
        "rating": 4.9,
        "review_count": 5800,
        "entry_fee": "Free",
        "timings": "05:00 AM – 09:00 PM",
        "tag": "UNESCO World Heritage",
        "week_rotation_group": 0,
        "is_featured": True
    },
    {
        "title": "Rajgir Glass Bridge & Nature Safari",
        "category": "TOURISM",
        "short_description": "Bihar's famous glass skywalk bridge suspended 200 feet high amidst the scenic hills, alongside dense wildlife safari and mountaineering.",
        "photo_url": "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.0210,
        "longitude": 85.4215,
        "address": "Nature Safari, Rajgir, Nalanda, Bihar 803116",
        "city": "Rajgir",
        "rating": 4.7,
        "review_count": 3900,
        "entry_fee": "₹125 Safari + ₹100 Glass Bridge",
        "timings": "09:00 AM – 05:00 PM (Closed Mon)",
        "tag": "Adventure Skywalk",
        "week_rotation_group": 0,
        "is_featured": True
    },
    {
        "title": "Golghar Historical Granary & Ganga View",
        "category": "HISTORICAL",
        "short_description": "Pillarless beehive-shaped granary erected in 1786. Dual spiral staircases lead to a panoramic birds-eye view of Patna city and the Ganga.",
        "photo_url": "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.6174,
        "longitude": 85.1432,
        "address": "Ashok Rajpath, Near Gandhi Maidan, Patna 800001",
        "city": "Patna",
        "rating": 4.6,
        "review_count": 4120,
        "entry_fee": "₹15",
        "timings": "09:30 AM – 06:00 PM",
        "tag": "Architectural Wonder",
        "week_rotation_group": 0,
        "is_featured": True
    },

    # Group 1 (Week 2 / Rotation B)
    {
        "title": "Nalanda Mahavihara Ancient University Ruins",
        "category": "HISTORICAL",
        "short_description": "UNESCO World Heritage ancient university from 5th century CE. Houses stunning red-brick stupas, scholastic monasteries, and meditation cells.",
        "photo_url": "https://images.unsplash.com/photo-1600100397608-f010f4439c2c?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.1357,
        "longitude": 85.4452,
        "address": "Bargaon, Nalanda, Bihar 803111",
        "city": "Nalanda",
        "rating": 4.9,
        "review_count": 4700,
        "entry_fee": "₹40 (ASI Monument)",
        "timings": "09:00 AM – 05:30 PM",
        "tag": "Ancient World University",
        "week_rotation_group": 1,
        "is_featured": True
    },
    {
        "title": "Vishwa Shanti Stupa & Historic Ropeway, Rajgir",
        "category": "TOURISM",
        "short_description": "Magnificent white marble World Peace Pagoda atop Ratnagiri Hill, reachable by India's oldest single-chair scenic aerial ropeway.",
        "photo_url": "https://images.unsplash.com/photo-1564507592333-c60657eea523?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.0069,
        "longitude": 85.4217,
        "address": "Ratnagiri Hill, Rajgir, Bihar 803116",
        "city": "Rajgir",
        "rating": 4.8,
        "review_count": 3200,
        "entry_fee": "₹100 (Ropeway return)",
        "timings": "08:00 AM – 05:00 PM",
        "tag": "Aerial Ropeway & Stupa",
        "week_rotation_group": 1,
        "is_featured": True
    },
    {
        "title": "Kesariya Great Buddhist Stupa",
        "category": "HISTORICAL",
        "short_description": "One of the tallest ancient Buddhist stupas in the world (104 feet high), built during the Mauryan and Gupta dynasties.",
        "photo_url": "https://images.unsplash.com/photo-1599839575945-a9e5af0c3fa5?auto=format&fit=crop&w=800&q=80",
        "latitude": 26.3533,
        "longitude": 84.8732,
        "address": "Kesariya, East Champaran, Bihar 845424",
        "city": "Champaran",
        "rating": 4.7,
        "review_count": 1850,
        "entry_fee": "₹20",
        "timings": "Sunrise to Sunset",
        "tag": "World's Tallest Stupa",
        "week_rotation_group": 1,
        "is_featured": True
    },
    {
        "title": "Takht Sri Harmandir Ji Patna Sahib",
        "category": "SPIRITUAL_HERITAGE",
        "short_description": "Birthplace of the Tenth Sikh Guru, Sri Guru Gobind Singh Ji Maharaj (1666 CE). Glorious white marble shrine along the banks of Holy Ganga.",
        "photo_url": "https://images.unsplash.com/photo-1584551246679-0daf3d275d0f?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.5941,
        "longitude": 85.2285,
        "address": "Harmandir Gali, Patna City, Bihar 800008",
        "city": "Patna",
        "rating": 4.9,
        "review_count": 6400,
        "entry_fee": "Free",
        "timings": "Open 24 Hours",
        "tag": "Sacred Takht Sahib",
        "week_rotation_group": 1,
        "is_featured": True
    },

    # Group 2 (Week 3 / Rotation C)
    {
        "title": "Barabar Ancient Rock-Cut Echo Caves",
        "category": "HISTORICAL",
        "short_description": "Oldest surviving rock-cut caves in India, carved in granite during Emperor Ashoka's reign (3rd century BCE) with flawless mirror polish.",
        "photo_url": "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.0065,
        "longitude": 85.0601,
        "address": "Makhdumpur, Jehanabad, Bihar 804422",
        "city": "Jehanabad",
        "rating": 4.7,
        "review_count": 1600,
        "entry_fee": "₹25",
        "timings": "06:00 AM – 06:00 PM",
        "tag": "Mauryan Caves",
        "week_rotation_group": 2,
        "is_featured": True
    },
    {
        "title": "Tomb of Sher Shah Suri, Sasaram",
        "category": "HISTORICAL",
        "short_description": "Indo-Islamic architectural masterpiece standing 122 feet high in the center of an artificial lake. Often celebrated as the second Taj Mahal.",
        "photo_url": "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?auto=format&fit=crop&w=800&q=80",
        "latitude": 24.9497,
        "longitude": 84.0204,
        "address": "Old GT Road, Sasaram, Rohtas, Bihar 821115",
        "city": "Sasaram",
        "rating": 4.8,
        "review_count": 2900,
        "entry_fee": "₹25",
        "timings": "06:00 AM – 06:00 PM",
        "tag": "Lake Mausoleum",
        "week_rotation_group": 2,
        "is_featured": True
    },
    {
        "title": "Pawapuri Jal Mandir",
        "category": "SPIRITUAL_HERITAGE",
        "short_description": "Breathtaking white marble temple situated in the midst of a blooming lotus lake. Sacred nirvana site of 24th Tirthankara Bhagwan Mahavira.",
        "photo_url": "https://images.unsplash.com/photo-1596402184320-417e7178b2cd?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.0931,
        "longitude": 85.5255,
        "address": "Pawapuri, Nalanda District, Bihar 803115",
        "city": "Pawapuri",
        "rating": 4.9,
        "review_count": 2150,
        "entry_fee": "Free",
        "timings": "06:00 AM – 08:30 PM",
        "tag": "Jain Nirvana Tirtha",
        "week_rotation_group": 2,
        "is_featured": True
    },
    {
        "title": "Vikramshila Ancient Buddhist University Ruins",
        "category": "HISTORICAL",
        "short_description": "Renowned Pala-era Buddhist university founded by King Dharmapala in 8th century CE. Houses an imposing cruciform stupa and monastic halls.",
        "photo_url": "https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.3283,
        "longitude": 87.2890,
        "address": "Antichak, Kahalgaon, Bhagalpur, Bihar 813203",
        "city": "Bhagalpur",
        "rating": 4.6,
        "review_count": 1300,
        "entry_fee": "₹25",
        "timings": "09:00 AM – 05:00 PM",
        "tag": "Pala Dynasty Heritage",
        "week_rotation_group": 2,
        "is_featured": True
    },

    # Group 3 (Week 4 / Rotation D)
    {
        "title": "Buddha Smriti Park & Karuna Stupa, Patna",
        "category": "TOURISM",
        "short_description": "22-acre tranquil urban heritage park blessed by Dalai Lama. Houses the 200-foot high Pataliputra Karuna Stupa and relics of Buddha.",
        "photo_url": "https://images.unsplash.com/photo-1569154941061-e231b4725ef1?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.6062,
        "longitude": 85.1382,
        "address": "Frazer Road, Near Patna Junction, Bihar 800001",
        "city": "Patna",
        "rating": 4.7,
        "review_count": 4200,
        "entry_fee": "₹20 Park + ₹50 Stupa",
        "timings": "09:00 AM – 07:00 PM (Closed Mon)",
        "tag": "Urban Memorial",
        "week_rotation_group": 3,
        "is_featured": True
    },
    {
        "title": "Ghora Katora Eco Lake & Buddha Statue",
        "category": "TOURISM",
        "short_description": "Pristine horse-shoe shaped natural lake nestled between hills. Only electric eco-vehicles allowed, featuring a 70-foot pink sandstone Buddha.",
        "photo_url": "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80",
        "latitude": 24.9875,
        "longitude": 85.4528,
        "address": "Ghora Katora, Rajgir, Nalanda, Bihar 803116",
        "city": "Rajgir",
        "rating": 4.8,
        "review_count": 2750,
        "entry_fee": "₹50 (Pure Electric Eco-Zone)",
        "timings": "08:00 AM – 05:00 PM",
        "tag": "Eco-Lake & Buddha",
        "week_rotation_group": 3,
        "is_featured": True
    },
    {
        "title": "Maner Sharif Historic Sufi Dargah",
        "category": "HISTORICAL",
        "short_description": "Grand Chunar sandstone 17th-century Mughal architecture with intricate stone jali work and expansive domed royal pavilions.",
        "photo_url": "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=800&q=80",
        "latitude": 25.6483,
        "longitude": 84.8821,
        "address": "Maner, Patna District, Bihar 801108",
        "city": "Maner",
        "rating": 4.6,
        "review_count": 1980,
        "entry_fee": "Free",
        "timings": "06:00 AM – 09:00 PM",
        "tag": "Mughal Carvings",
        "week_rotation_group": 3,
        "is_featured": True
    },
    {
        "title": "Rohtasgarh Ancient Hill Fort",
        "category": "HISTORICAL",
        "short_description": "One of the oldest and largest hill forts in India perched 1,500 feet atop Kaimur plateau overlooking the scenic Son River valley.",
        "photo_url": "https://images.unsplash.com/photo-1599839575945-a9e5af0c3fa5?auto=format&fit=crop&w=800&q=80",
        "latitude": 24.6310,
        "longitude": 83.9160,
        "address": "Rohtas Plateau, Rohtas District, Bihar 821311",
        "city": "Rohtas",
        "rating": 4.7,
        "review_count": 1450,
        "entry_fee": "₹25",
        "timings": "08:00 AM – 05:00 PM",
        "tag": "Kaimur Hilltop Fort",
        "week_rotation_group": 3,
        "is_featured": True
    }
]

async def sync_tourism():
    print("Connecting to Supabase PostgreSQL...")
    engine = create_async_engine(
        db_url,
        echo=False,
        future=True,
        connect_args={"ssl": "require"}
    )

    # 1. Ensure table exists
    async with engine.begin() as conn:
        print("Ensuring tourism_places table exists...")
        await conn.run_sync(Base.metadata.create_all)
    print("tourism_places table ready!")

    # 2. Check and seed
    session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with session_factory() as session:
        res = await session.execute(select(TourismPlace))
        existing = res.scalars().all()
        print(f"Existing tourism places in database: {len(existing)}")

        # Upsert or seed
        existing_titles = {p.title for p in existing}
        inserted = 0
        for data in PLACES_DATA:
            if data["title"] not in existing_titles:
                place = TourismPlace(**data)
                session.add(place)
                inserted += 1

        if inserted > 0:
            await session.commit()
            print(f"Successfully inserted {inserted} famous historical & tourism places into Supabase!")
        else:
            print("All places already exist in database.")

    await engine.dispose()
    print("Done!")

if __name__ == "__main__":
    asyncio.run(sync_tourism())
