# 🚂 Railway Deployment Guide - UPDATED

## ✅ What Was Fixed

**Problem:** Railway couldn't detect the project type because `pom.xml` was nested in `udriBook/`

**Solution:** Created a root-level `pom.xml` wrapper that tells Railway this is a Maven project.

---

## 📁 Files Created/Updated

### Root Level Files:
- ✅ `pom.xml` - Parent POM for Railway detection
- ✅ `railway.toml` - Railway deployment config
- ✅ `nixpacks.toml` - Build configuration
- ✅ `Procfile` - Process configuration
- ✅ `build.sh` - Build script
- ✅ `start.sh` - Start script
- ✅ `.railwayignore` - Files to exclude from deployment

### Updated:
- ✅ `udriBook/src/main/resources/application.properties` - Now uses environment variables

---

## 🚀 Deploy to Railway - Step by Step

### **Option 1: Railway Web UI (Easiest)**

#### Step 1: Push to GitHub
```bash
cd c:\Users\shiva\backend\Project
git add .
git commit -m "Configure for Railway deployment"
git push origin main
```

#### Step 2: Deploy on Railway
1. Go to https://railway.app
2. Click **"New Project"**
3. Select **"Deploy from GitHub repo"**
4. Choose your repository
5. Railway will automatically detect it as a Java/Maven project and start building

#### Step 3: Add MySQL Database
1. In your Railway project dashboard, click **"+ New"**
2. Select **"Database"** → **"MySQL"**
3. Wait for MySQL to provision

#### Step 4: Configure Environment Variables
Railway auto-provides these from MySQL:
- `MYSQLHOST`
- `MYSQLPORT`
- `MYSQLDATABASE`
- `MYSQLUSER`
- `MYSQLPASSWORD`

You need to add these custom variables:
```
DATABASE_URL=jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}
DB_USERNAME=${MYSQLUSER}
DB_PASSWORD=${MYSQLPASSWORD}
JWT_SECRET=your-secret-jwt-key-change-this-in-production
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your-app-password
```

#### Step 5: Add Redis (Optional - for OTP storage)
1. Click **"+ New"** → **"Database"** → **"Redis"**
2. Railway will auto-provide: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`

#### Step 6: Deploy!
Railway will automatically build and deploy your app.

---

### **Option 2: Railway CLI**

```bash
# Install Railway CLI
npm install -g @railway/cli

# Login to Railway
railway login

# Initialize project in current directory
cd c:\Users\shiva\backend\Project
railway init

# Deploy
railway up

# Add MySQL database
railway add mysql

# Add Redis (optional)
railway add redis

# Set custom environment variables
railway variables set JWT_SECRET=your-secret-key
railway variables set EMAIL_USERNAME=your-email@gmail.com
railway variables set EMAIL_PASSWORD=your-app-password
```

---

## 🔍 Verify Deployment

### Check Build Logs
1. Go to Railway dashboard
2. Click on your service
3. View **"Deployments"** tab
4. Check build logs - should show:
   - ✅ Detected Java/Maven project
   - ✅ Building with JDK 17
   - ✅ Maven build successful
   - ✅ Application starting

### Check Application Logs
Look for:
```
Started UdriBookApplication in X.XXX seconds
Tomcat started on port(s): XXXX (http)
```

### Test the API
Railway will provide a URL like: `https://your-app.railway.app`

Test with:
```bash
curl https://your-app.railway.app/api/health
```

---

## ⚙️ Environment Variables Reference

### Required:
| Variable | Description | Auto-Provided by Railway? |
|----------|-------------|---------------------------|
| `PORT` | Application port | ✅ Yes |
| `DATABASE_URL` | JDBC connection string | ❌ No (create from MySQL vars) |
| `DB_USERNAME` | Database username | ❌ No (use `${MYSQLUSER}`) |
| `DB_PASSWORD` | Database password | ❌ No (use `${MYSQLPASSWORD}`) |

### Optional (Production):
| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | JWT signing key | Uses default (CHANGE THIS!) |
| `EMAIL_USERNAME` | SMTP username | Uses default |
| `EMAIL_PASSWORD` | SMTP password | Uses default |
| `REDIS_HOST` | Redis host | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `REDIS_PASSWORD` | Redis password | empty |

---

## ⚠️ Important Notes

### 1. **File Uploads Will Be Lost**
Railway has ephemeral storage. Files uploaded to `/uploads` will be deleted on redeploy.

**Solutions:**
- Use Railway Volumes (persistent storage)
- Use cloud storage (AWS S3, Cloudinary, Google Cloud Storage)

### 2. **Database Schema**
The app uses `spring.jpa.hibernate.ddl-auto=update`, so tables will be created automatically on first run.

### 3. **Security**
🚨 **IMPORTANT:** Change these in production:
- Generate a new `JWT_SECRET` (use a random 256-bit key)
- Use app-specific passwords for email, not your main password
- Never commit secrets to Git

Generate a secure JWT secret:
```bash
# Linux/Mac/Git Bash
openssl rand -base64 64

# Or use any password generator
```

### 4. **Redis for OTP**
Your app uses Redis for OTP storage. Make sure to add Redis in Railway if you need OTP functionality.

---

## 🐛 Troubleshooting

### Build Fails with "pom.xml not found"
- ✅ **Fixed!** We created a root `pom.xml`

### "Database connection failed"
- Check that MySQL is added to your Railway project
- Verify `DATABASE_URL` is in correct JDBC format
- Ensure it references `${MYSQLHOST}`, `${MYSQLPORT}`, etc.

### "Port already in use"
- Railway auto-sets `$PORT` - make sure your app uses it
- ✅ **Fixed!** `application.properties` uses `${PORT:8081}`

### Application crashes on startup
- Check Railway logs for Java exceptions
- Verify all required environment variables are set
- Ensure Redis is added if OTP features are used

---

## 📊 Expected Build Output

You should see something like:
```
Building with Nixpacks
└─ Detected Java/Maven project
└─ Installing JDK 17
└─ Running: cd udriBook && ./mvnw clean package -DskipTests
   [INFO] BUILD SUCCESS
└─ Built: udriBook/target/MyKhata-0.0.1-SNAPSHOT.jar
Starting application...
└─ Running: cd udriBook && java -Dserver.port=$PORT -jar target/*.jar
```

---

## 🎯 Next Steps After Deployment

1. **Test all endpoints** with your production URL
2. **Configure CORS** if you have a frontend (update Spring Security config)
3. **Set up monitoring** (Railway provides basic metrics)
4. **Configure custom domain** (if needed, in Railway settings)
5. **Set up file storage** (if using uploads feature)

---

## 📞 Need Help?

- Railway Docs: https://docs.railway.app
- Railway Community: https://discord.gg/railway
- Spring Boot Docs: https://docs.spring.io/spring-boot/

---

**Good luck with your deployment! 🚀**
