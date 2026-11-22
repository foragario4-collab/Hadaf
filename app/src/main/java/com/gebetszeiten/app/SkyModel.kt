package com.gebetszeiten.app

import android.graphics.RuntimeShader
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Himmels-Shader (AGSL, ab Android 13). Volumenwolken, Sonne, Mond mit Phase, Sterne, Regen, Schnee, Nebel. */
internal const val SKY_AGSL = """
uniform float2 uRes; uniform float uTime;
uniform float3 uTop; uniform float3 uMid; uniform float3 uHor;
uniform float2 uSun; uniform float uSunVis; uniform float3 uSunCol; uniform float uSunLow;
uniform float2 uMoon; uniform float uMoonVis; uniform float uPhase;
uniform float uStars; uniform float uCloud; uniform float uRain; uniform float uSnow; uniform float uFog;
uniform float uFlash; uniform float uHorizon; uniform float2 uFlashPos; uniform float uWind;
uniform float3 uCloudLit; uniform float3 uCloudShade; uniform float uSteps; uniform float uLight; uniform float uDisc;

float hash(float2 p){p=fract(p*float2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
float noise(float2 p){float2 i=floor(p);float2 f=fract(p);float2 u=f*f*(3.-2.*f);
  return mix(mix(hash(i),hash(i+float2(1,0)),u.x),mix(hash(i+float2(0,1)),hash(i+float2(1,1)),u.x),u.y);}
float fbm(float2 p){float v=0.;float a=.5;float2x2 r=float2x2(.8,.6,-.6,.8);for(int i=0;i<5;i++){v+=a*noise(p);p=r*p*2.03+float2(1.7,9.2);a*=.5;}return v;}
float fbm3(float2 p){float v=0.;float a=.5;float2x2 r=float2x2(.8,.6,-.6,.8);for(int i=0;i<3;i++){v+=a*noise(p);p=r*p*2.03+float2(1.7,9.2);a*=.5;}return v;}
float hash31(float3 p){p=fract(p*.3183099+.1);p*=17.;return fract(p.x*p.y*p.z*(p.x+p.y+p.z));}
float noise3(float3 x){float3 i=floor(x);float3 f=fract(x);f=f*f*(3.-2.*f);
  return mix(mix(mix(hash31(i),hash31(i+float3(1,0,0)),f.x),mix(hash31(i+float3(0,1,0)),hash31(i+float3(1,1,0)),f.x),f.y),
             mix(mix(hash31(i+float3(0,0,1)),hash31(i+float3(1,0,1)),f.x),mix(hash31(i+float3(0,1,1)),hash31(i+float3(1,1,1)),f.x),f.y),f.z);}
float fbm3d(float3 p){float v=0.;float a=.5;for(int i=0;i<4;i++){v+=a*noise3(p);p=p*2.03+float3(1.3,7.1,3.7);a*=.5;}return v;}
float cloudDens(float3 pos, float cov, float t){
  float hf=(pos.y-1.0)/1.6;
  if(hf<0.||hf>1.) return 0.;
  float wm=fbm3(pos.xz*.13+float2(t*.008*uWind,t*.003*uWind));
  float wmask=smoothstep(1.-cov*1.05,1.-cov*1.05+.28,wm);
  if(wmask<=0.) return 0.;
  float top=mix(.45,1.,wmask);
  float prof=smoothstep(0.,.10,hf)*smoothstep(top,top*.55,hf);
  float3 sp=pos*float3(.38,.62,.38)+float3(t*.06*uWind,t*.004,t*.022*uWind);
  float base=fbm3d(sp);
  float shape=clamp((base-(1.-wmask*.85))/(wmask*.85+.001),0.,1.)*prof;
  if(shape<=0.) return 0.;
  float b1=1.-abs(2.*noise3(sp*4.1+float3(0.,t*.02,0.))-1.);
  shape-=(1.-b1)*.42*(1.-shape)*(1.-hf*.3);
  return clamp(shape*2.6,0.,1.);
}
half4 main(float2 fc){
  float2 uv=float2(fc.x/uRes.x,1.-fc.y/uRes.y);
  float asp=uRes.x/uRes.y; float2 p=float2(uv.x*asp,uv.y); float hz=uHorizon;
  float hgt=clamp((uv.y-hz)/(1.-hz),0.,1.);
  float3 col=mix(uHor,uMid,smoothstep(0.,.42,hgt));
  col=mix(col,uTop,smoothstep(.38,1.,hgt));
  float2 sp=float2(uSun.x*asp,uSun.y); float2 dv=p-sp; float d=length(dv);
  col+=uSunCol*(.38*exp(-d*7.5)+.18*exp(-d*2.2))*uSunVis;
  col+=uSunCol*uSunLow*.35*exp(-abs(uv.y-hz)*9.)*exp(-abs(dv.x)*1.6)*uSunVis;
  // Sterne
  float starMask=uStars*smoothstep(hz+.08,hz+.4,uv.y);
  float2 g=floor(p*150.); float h=hash(g); float2 gf=fract(p*150.)-.5;
  col+=float3(.9,.95,1.)*step(.9972,h)*smoothstep(.25,0.,length(gf))*(.55+.45*sin(uTime*(1.1+h*3.)+h*70.))*starMask;
  // Mond als Kugel
  float2 mp=float2(uMoon.x*asp,uMoon.y); float R=.045; float2 q=(p-mp)/R; float r2=dot(q,q);
  col+=float3(.7,.78,1.)*(.10*exp(-length(p-mp)*14.))*uMoonVis;
  if(r2<1.){
    float3 n=float3(q.x,-q.y,sqrt(1.-r2));
    float ang=uPhase*6.2831853;
    float3 L=normalize(float3(sin(ang),0.,-cos(ang)));
    float diff=smoothstep(-.06,.12,dot(n,L));
    float maria=fbm3(q*2.3+3.)*.6+fbm3(q*7.+1.)*.25;
    float3 surf=mix(float3(.62,.63,.66),float3(.98,.97,.94),smoothstep(.35,.75,maria));
    float3 moonCol=mix(float3(.10,.12,.18),surf,diff);
    col=mix(col,moonCol,smoothstep(1.,.96,r2)*uMoonVis);
  }
  // Sonnenscheibe
  float sd=smoothstep(.055,.047,d);
  col=mix(col,min(uSunCol*1.25+.25,float3(1.)),sd*uSunVis*uDisc);
  // Volumenwolken
  if(uCloud>.01 && uv.y>hz-.01){
    float3 dir=normalize(float3((uv.x-.5)*asp*1.15,(uv.y-hz)*1.15+.004,1.));
    float3 sunDir=normalize(float3((uSun.x-.5)*asp*1.15,(uSun.y-hz)*1.15+.02,1.));
    if(dir.y>.002){
      float t0=1.0/dir.y; float t1=min(2.6/dir.y,t0+9.);
      float dt=(t1-t0)/uSteps;
      float tt=t0+dt*fract(52.9829189*fract(dot(fc,float2(.06711056,.00583715))));
      float T=1.; float3 acc=float3(0.);
      float cosT=dot(dir,sunDir);
      float hgF=(1.-.62*.62)/pow(1.+.62*.62-2.*.62*cosT,1.5);
      float hgB=(1.-.0625)/pow(1.0625+.5*cosT,1.5);
      float hg=mix(hgF,hgB,.3)*.22+.32;
      float toward=smoothstep(-.3,1.,cosT);
      float2 fp=float2(uFlashPos.x*asp,uFlashPos.y);
      float fl=uFlash*(exp(-length(p-fp)*3.2)*1.6+exp(-length(p-fp)*1.1)*.35);
      for(int i=0;i<30;i++){
        if(float(i)>=uSteps || T<.03) break;
        float3 pos=dir*tt;
        float dn=cloudDens(pos,uCloud,uTime);
        if(dn>.004){
          float ld=cloudDens(pos+sunDir*.22,uCloud,uTime)*1.6;
          if(uLight>.5) ld=cloudDens(pos+sunDir*.12,uCloud,uTime)+cloudDens(pos+sunDir*.45,uCloud,uTime);
          float hf=clamp((pos.y-1.)/1.6,0.,1.);
          float beer=exp(-ld*1.5);
          float multi=exp(-ld*.38)*.45;
          float powder=1.-exp(-dn*2.6);
          float3 skyAmb=mix(uCloudShade,mix(uCloudLit,uTop*.6+.45,.25),.2+.8*hf);
          skyAmb*=mix(float3(1.),float3(.86,.88,1.06),uSunLow*(1.-toward)*.6);
          float3 sunL=uSunCol*uSunVis*(beer*hg*1.9+multi)*mix(.7,1.,powder)*1.7;
          sunL+=uSunCol*uSunVis*pow(max(cosT,0.),10.)*(1.-exp(-dn*1.2))*beer*1.6;
          float3 sc=skyAmb*(.62+.25*beer)+sunL+uSunCol*uSunLow*uSunVis*(1.-hf)*.4
                   +float3(.80,.86,1.)*fl*(1.15-hf*.3)*(.5+.5*powder);
          sc=mix(sc,mix(uHor,uMid,.3),smoothstep(2.5,13.,tt)*.55);
          float a=1.-exp(-dn*dt*3.2);
          acc+=T*a*sc; T*=1.-a;
        }
        tt+=dt;
      }
      float far=smoothstep(2.,12.,t0);
      float3 cc=col*T+mix(acc,(1.-T)*mix(uHor,uMid,.35),far*.75);
      // knapp über dem Horizont weich ausblenden (dort entstanden Streifen und Einzelpixel)
      col=mix(col*(1.-uCloud*.25)+mix(uHor,uMid,.35)*uCloud*.25,cc,smoothstep(.004,.07,dir.y));
    }
  }
  // Nebel
  float fogN=fbm(float2(p.x*1.3+uTime*.03,p.y*4.-uTime*.012));
  float fogM=uFog*(smoothstep(.75,hz,uv.y)*.85+.15)*(.45+.55*fogN);
  col=mix(col,mix(float3(.80,.83,.88),uHor*1.1,.35)+uSunCol*exp(-d*2.)*.25*uSunVis,clamp(fogM,0.,.92));
  // Regen
  float rain=0.;
  for(int i=0;i<3;i++){float fi=float(i);
    float2 rp=p; rp.x+=rp.y*(.06+.16*uWind+fi*.03);
    rp*=float2(55.+fi*50.,1.6+fi*.7); rp.y+=uTime*(6.+fi*3.);
    float2 id=mod(floor(rp),289.); float2 f=fract(rp); float r=hash(id+fi*13.);
    float len=.35+.4*hash(id+3.);
    rain+=step(.88,r)*smoothstep(.16,0.,abs(f.x-.5))*smoothstep(1.-len,1.,f.y)*smoothstep(1.,.92,f.y)*(.18+.24*fi)*step(fi*.34,uRain);}
  col=mix(col,col*.82+float3(.04,.05,.07),min(uRain,1.)*.5);
  col+=float3(.8,.85,.95)*rain*min(1.,.4+uRain*.6)*step(.001,uRain);
  // Schnee
  float snow=0.;
  for(int i=0;i<3;i++){float fi=float(i);
    float2 s2=p*(9.-fi*3.); s2.y+=uTime*(.18+fi*.14); s2.x+=sin(uTime*.6+s2.y*1.1+fi*2.)*.3;
    float2 id=mod(floor(s2),289.); float2 f=fract(s2); float2 c=float2(.5)+(float2(hash(id+1.),hash(id+2.))-.5)*.6;
    float size=.05+fi*.04; float soft=.02+fi*.05;
    snow+=smoothstep(size+soft,size*.3,length(f-c))*step(.55,hash(id+fi))*(.55+.2*fi);}
  col=mix(col,col*.92+float3(.05),uSnow*.4);
  col+=float3(.95,.97,1.)*snow*uSnow;
  col+=float3(.5,.56,.78)*uFlash*(.05+.18*exp(-length(p-float2(uFlashPos.x*asp,uFlashPos.y))*1.8));
  col+=(hash(fc+fract(uTime))-.5)/255.;
  return half4(half3(col),1.);
}
"""

internal fun hexC(s: String): FloatArray {
    val v = s.removePrefix("#").toLong(16)
    return floatArrayOf(((v shr 16) and 255) / 255f, ((v shr 8) and 255) / 255f, (v and 255) / 255f)
}
internal fun mixC(a: FloatArray, b: FloatArray, t: Float) = FloatArray(3) { a[it] + (b[it] - a[it]) * t }
internal fun lum(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]

/** Alles, was der Himmel für einen Zeitpunkt braucht (aus echten Gebetszeiten + Wetter). */
internal class SkyState(
    val top: FloatArray, val mid: FloatArray, val hor: FloatArray,
    val sunX: Float, val sunY: Float, val sunVis: Float, val sunCol: FloatArray, val sunLow: Float,
    val moonX: Float, val moonY: Float, val moonVis: Float, val phase: Float, val stars: Float,
    val cloud: Float, val rain: Float, val snow: Float, val fog: Float, val wind: Float,
    val lit: FloatArray, val shade: FloatArray, val night: Boolean, val dusk: Boolean, val thunder: Boolean
)

internal fun skyState(now: Long, wx: Wx?): SkyState {
    val today = Times.today(now)
    val evs = Times.events(today, 1).filter { it.day == today }
    fun mins(k: Kind, def: Int): Float {
        val e = evs.firstOrNull { it.kind == k } ?: return def.toFloat()
        val z = java.time.Instant.ofEpochMilli(e.at).atZone(Times.zone())
        return (z.hour * 60 + z.minute).toFloat()
    }
    val fajr = mins(Kind.FAJR, 322); val sun = mins(Kind.SUNRISE, 410); val dhuhr = mins(Kind.DHUHR, 698)
    val asr = mins(Kind.ASR, 841); val magh = mins(Kind.MAGHRIB, 985); val isha = mins(Kind.ISHA, 1086)
    val z = java.time.Instant.ofEpochMilli(now).atZone(Times.zone())
    val m = z.hour * 60f + z.minute + z.second / 60f
    val stops = listOf(
        0f to listOf("#050913", "#0B1226", "#151B36"), fajr - 30 to listOf("#070D20", "#121B3C", "#232750"),
        fajr + 25 to listOf("#16224A", "#3A3F7A", "#B56F78"), sun to listOf("#2A4682", "#7A82B8", "#F3A774"),
        sun + 60 to listOf("#3769B6", "#79A6DE", "#F4D2A8"), sun + 150 to listOf("#2D72CC", "#6AAAE8", "#B7DCF8"),
        dhuhr to listOf("#2569C6", "#5EA2EA", "#A7D3FA"), asr to listOf("#2B67B8", "#78A8DD", "#E9D4A6"),
        magh - 35 to listOf("#36518F", "#B07A7E", "#F2A869"), magh to listOf("#2C2F68", "#8A4E78", "#EE8A5C"),
        magh + 35 to listOf("#171C45", "#3E2F62", "#7E4A62"), isha to listOf("#0B1229", "#16204A", "#2A2A55"),
        1440f to listOf("#050913", "#0B1226", "#151B36")
    ).sortedBy { it.first }
    var i = 0
    while (i < stops.size - 2 && m >= stops[i + 1].first) i++
    val (m0, c0) = stops[i]; val (m1, c1) = stops[i + 1]
    var tt = ((m - m0) / max(1f, m1 - m0)).coerceIn(0f, 1f); tt = tt * tt * (3 - 2 * tt)
    var cols = (0..2).map { mixC(hexC(c0[it]), hexC(c1[it]), tt) }
    val w = wx ?: Wx(0.2f, 0f, false, false, 0f, 0.25f, null, 0L)
    val grey = when {
        w.thunder && w.cloud > 0.6f -> 0.75f
        w.rain > 0.05f -> 0.6f
        w.fog > 0.5f -> 0.5f
        w.cloud > 0.5f -> 0.35f * w.cloud
        else -> 0f
    }
    val greyTo = if (w.thunder) listOf(hexC("#2B2F3D"), hexC("#4A4F60")) else if (w.fog > 0.5f) listOf(hexC("#9AA1AE"), hexC("#C3C8D1")) else listOf(hexC("#5B6273"), hexC("#8C93A3"))
    if (grey > 0f) cols = cols.mapIndexed { k, c -> mixC(c, if (k == 0) greyTo[0] else greyTo[1], grey) }
    val horizon = 0.12f
    val pSun = (m - (sun - 25)) / ((magh + 25) - (sun - 25))
    val sunUp = pSun > -0.02f && pSun < 1.02f
    val low = pSun < 0.14f || pSun > 0.86f
    val ps = pSun.coerceIn(0f, 1f)
    val sunX = 0.08f + ps * 0.84f
    val sunY = horizon + sin(ps * PI.toFloat()) * 0.62f - (if (sunUp) 0f else 0.2f)
    val nightLen = 1440 - (magh - sun)
    val mm = if (m >= magh) m - magh else if (m < sun) m + 1440 - magh else -1f
    val pMoon = if (mm < 0) -1f else mm / nightLen
    val moonUp = pMoon > 0.03f && pMoon < 0.97f
    val nightness = if (mm < 0) 0f else (min(mm, nightLen - mm) / 70f).coerceIn(0f, 1f)
    val dusk = abs(m - sun) < 55 || abs(m - magh) < 55
    val night = m > isha || m < fajr
    val syn = 29.530588853
    val age = (((now / 86400000.0 - 10962.5) % syn) + syn) % syn
    var lit = if (night) hexC("#56608A") else if (dusk) hexC("#FFE1CF") else hexC("#FFFFFF")
    var shade = if (night) hexC("#1A1F38") else if (dusk) hexC("#7A5F86") else hexC("#A9B6CC")
    if (w.rain > 0.05f || w.snow) { lit = mixC(lit, hexC("#B8BDC8"), 0.5f); shade = mixC(shade, hexC("#5A6070"), 0.6f) }
    if (w.thunder && w.cloud > 0.6f) { lit = hexC("#6B7183"); shade = hexC("#262A38") }
    val sunVis = if (!sunUp) 0f else when {
        w.rain > 0.05f || w.thunder -> 0.25f
        w.fog > 0.5f -> 0.45f
        w.cloud > 0.5f -> 0.75f
        else -> 1f
    }
    return SkyState(
        cols[0], cols[1], cols[2], sunX, sunY, sunVis,
        if (low) hexC("#FF9A5A") else hexC("#FFE6A8"),
        if (low) 1f else max(0f, 1f - min(abs(m - sun), abs(m - magh)) / 120f),
        0.14f + max(0f, pMoon) * 0.72f, horizon + 0.1f + sin(pMoon.coerceIn(0f, 1f) * PI.toFloat()) * 0.48f,
        if (moonUp) (if (w.cloud < 0.5f && w.rain < 0.05f) 1f else 0.4f) else 0f,
        (age / syn).toFloat(),
        if (w.rain < 0.05f && w.cloud < 0.6f) nightness else nightness * 0.3f,
        max(0.05f, w.cloud), if (w.snow) 0f else w.rain, if (w.snow) min(1f, w.rain * 1.6f + 0.2f) else 0f,
        w.fog, 0.4f + w.wind * 2.2f, lit, shade, night, dusk, w.thunder
    )
}


/** Shader einmal erzeugen; schlägt das fehl (z. B. Grafiktreiber), wird ein einfacher Verlauf gezeigt. */
internal object SkyShader {
    private var shader: Any? = null
    private var failed = false
    fun ok(): Boolean = get() != null
    fun create(): RuntimeShader? = if (Build.VERSION.SDK_INT < 33 || failed) null else try { RuntimeShader(SKY_AGSL) } catch (e: Throwable) { failed = true; null }
    @Volatile private var sharpS: Any? = null
    @Volatile private var blurS: Any? = null
    fun sharp(): RuntimeShader? { sharpS?.let { return it as RuntimeShader }; return create()?.also { sharpS = it } }
    fun blurred(): RuntimeShader? { blurS?.let { return it as RuntimeShader }; return create()?.also { blurS = it } }
    /** Beim App-Start im Hintergrund vorbereiten, damit „Heute“ sofort flüssig öffnet. */
    fun warmUp() {
        if (Build.VERSION.SDK_INT < 33) return
        Thread { try { get(); sharp(); blurred() } catch (e: Throwable) { } }.start()
    }
    @Suppress("UNCHECKED_CAST")
    fun get(): RuntimeShader? {
        if (Build.VERSION.SDK_INT < 33 || failed) return null
        shader?.let { return it as RuntimeShader }
        return try {
            RuntimeShader(SKY_AGSL).also { shader = it }
        } catch (e: Throwable) {
            failed = true
            null
        }
    }
}


