/* ranobelib 1.0.0 · api 1 · qjs-ng-0.12.1 · core 1.1.0 */
"use strict";
(() => {
  var __defProp = Object.defineProperty;
  var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== "symbol" ? key + "" : key, value);

  // plugins/ranobelib/manifest.json
  var manifest_default = {
    id: "ranobelib",
    name: "RanobeLib",
    version: "1.0.0",
    api: 1,
    lang: "ru",
    site: "https://ranobelib.me",
    domains: ["api.cdnlibs.org", "ranobelib.me"],
    icon: "icons/ranobelib.png",
    content: ["text"],
    features: ["popular", "latest", "search", "filters", "login", "byUrl"],
    nsfw: false,
    studio: "private",
    browser: "onChallenge",
    rateLimit: { rps: 1, burst: 3 },
    login: {
      url: "https://ranobelib.me/ru/front/auth",
      kind: "localStorage",
      key: "auth",
      path: "/token/access_token",
      header: "Authorization: Bearer {token}"
    }
  };

  // ../unison-sources/lib/core/version.ts
  var CORE_VERSION = "1.1.0";

  // ../unison-sources/lib/runtime.ts
  var pending = /* @__PURE__ */ new Map();
  var seq = 0;
  function request(op, args) {
    return new Promise((resolve, reject) => {
      const id = ++seq;
      pending.set(id, { resolve, reject });
      __host_request(id, op, JSON.stringify(args));
    });
  }
  var g = globalThis;
  g.__host_resolve = (id, json) => {
    const p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    p.resolve(JSON.parse(json));
  };
  g.__host_reject = (id, json) => {
    const p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    p.reject(JSON.parse(json));
  };
  function fetch(url, init = {}) {
    return request("fetch", [url, init]);
  }
  var storage = {
    get(key) {
      return request("storage.get", [key]);
    },
    set(key, value) {
      return request("storage.set", [key, value]);
    }
  };
  function fail(code, message, url) {
    const e = url ? { code, message, url } : { code, message };
    throw e;
  }
  function toError(e) {
    if (e && typeof e === "object" && typeof e.code === "string") {
      const pe = e;
      return pe.url ? { code: pe.code, message: String(pe.message), url: pe.url } : { code: pe.code, message: String(pe.message) };
    }
    const msg = e && typeof e === "object" && "message" in e ? String(e.message) : String(e);
    return { code: "ENGINE", message: msg };
  }
  var FUNCTIONS = ["popular", "latest", "search", "details", "chapters", "chapter", "filters", "bookIdFromUrl"];
  function definePlugin(manifest, plugin2) {
    g.__unison = {
      manifest,
      core: { version: CORE_VERSION },
      call(callId, fn, argsJson) {
        Promise.resolve().then(() => {
          if (!FUNCTIONS.includes(fn)) fail("NOT_FOUND", `unknown function ${fn}`);
          const f = plugin2[fn];
          if (typeof f !== "function") fail("NOT_FOUND", `${manifest.id} has no ${fn}()`);
          return f.apply(plugin2, JSON.parse(argsJson));
        }).then(
          (ok) => __host_done(callId, JSON.stringify({ ok })),
          (e) => __host_done(callId, JSON.stringify({ err: toError(e) }))
        );
      }
    };
  }

  // ../unison-sources/node_modules/entities/dist/decode-codepoint.js
  var c1 = [
    8364,
    0,
    8218,
    402,
    8222,
    8230,
    8224,
    8225,
    710,
    8240,
    352,
    8249,
    338,
    0,
    381,
    0,
    0,
    8216,
    8217,
    8220,
    8221,
    8226,
    8211,
    8212,
    732,
    8482,
    353,
    8250,
    339,
    0,
    382,
    376
  ];
  function isInvalidCodePoint(codePoint) {
    return codePoint === 0 || codePoint >= 55296 && codePoint <= 57343 || codePoint > 1114111;
  }
  function replaceCodePoint(codePoint) {
    if (isInvalidCodePoint(codePoint)) {
      return 65533;
    }
    if (codePoint >= 128 && codePoint <= 159) {
      return c1[codePoint - 128] || codePoint;
    }
    return codePoint;
  }
  function replaceCodePointXML(codePoint) {
    return isInvalidCodePoint(codePoint) ? 65533 : codePoint;
  }

  // ../unison-sources/node_modules/entities/dist/internal/decode-shared.js
  var BASE91_INVERSE = /* @__PURE__ */ (() => {
    const table = new Uint8Array(127);
    let code = 0;
    for (let char = 33; char <= 126; char++) {
      if (char !== 34 && char !== 36 && char !== 92) {
        table[char] = code++;
      }
    }
    return table;
  })();
  function decodeTrieDict(input, resultLength, atomCount, dict1AtomCount, ngramCount, dictSize) {
    const base = 91;
    const inputLength = input.length;
    const twoCharBias = dictSize * (base - 1);
    let pos = 0;
    const readSlotCode = () => {
      const c12 = BASE91_INVERSE[input.charCodeAt(pos++)];
      return c12 < dictSize ? c12 : c12 * base - twoCharBias + BASE91_INVERSE[input.charCodeAt(pos++)];
    };
    const dict2AtomCount = atomCount - dict1AtomCount;
    const slotCount = atomCount + ngramCount;
    const single = new Int32Array(slotCount);
    single.fill(-1, dict1AtomCount, dictSize);
    single.fill(-1, dictSize + dict2AtomCount, slotCount);
    const start = new Int32Array(slotCount);
    const length = new Int32Array(slotCount);
    function decodeDelta(count, off) {
      let previous = 0;
      let slot = off;
      const end = off + count;
      while (slot < end) {
        const code = BASE91_INVERSE[input.charCodeAt(pos++)];
        if (code < 89) {
          previous += code;
          single[slot++] = previous;
        } else if (code === 89) {
          let runLength = BASE91_INVERSE[input.charCodeAt(pos++)] + 2;
          while (runLength--)
            single[slot++] = ++previous;
        } else {
          const next = BASE91_INVERSE[input.charCodeAt(pos++)];
          previous += 89 + // eslint-disable-next-line unicorn/prefer-minimal-ternary -- branches read a different number of side-effecting input bytes
          (next < 90 ? next * base + BASE91_INVERSE[input.charCodeAt(pos++)] : BASE91_INVERSE[input.charCodeAt(pos++)] * 8281 + BASE91_INVERSE[input.charCodeAt(pos++)] * base + BASE91_INVERSE[input.charCodeAt(pos++)]);
          single[slot++] = previous;
        }
      }
    }
    decodeDelta(dict1AtomCount, 0);
    decodeDelta(dict2AtomCount, dictSize);
    const references = new Int32Array(ngramCount * 2);
    let poolSize = 0;
    let ngramIndex = 0;
    function readNgramReferences(count, startSlot) {
      for (let index = 0; index < count; index++) {
        const slot = startSlot + index;
        const a = readSlotCode();
        const b = readSlotCode();
        references[ngramIndex * 2] = a;
        references[ngramIndex * 2 + 1] = b;
        ngramIndex += 1;
        start[slot] = poolSize;
        const entryLength = (single[a] < 0 ? length[a] : 1) + (single[b] < 0 ? length[b] : 1);
        length[slot] = entryLength;
        poolSize += entryLength;
      }
    }
    readNgramReferences(ngramCount - dictSize + dict1AtomCount, dictSize + dict2AtomCount);
    readNgramReferences(dictSize - dict1AtomCount, dict1AtomCount);
    const pool = new Uint16Array(poolSize);
    let write = 0;
    for (let index = 0; index < ngramIndex; index++) {
      for (let half = 0; half < 2; half++) {
        const source = references[index * 2 + half];
        const value = single[source];
        if (value < 0) {
          let read = start[source];
          const readEnd = read + length[source];
          while (read < readEnd)
            pool[write++] = pool[read++];
        } else {
          pool[write++] = value;
        }
      }
    }
    const out = new Uint16Array(resultLength);
    let outIndex = 0;
    while (pos < inputLength) {
      let slot = BASE91_INVERSE[input.charCodeAt(pos++)];
      if (slot >= dictSize) {
        slot = slot * base - twoCharBias + BASE91_INVERSE[input.charCodeAt(pos++)];
      }
      const value = single[slot];
      if (value < 0) {
        let read = start[slot];
        const readEnd = read + length[slot];
        while (read < readEnd)
          out[outIndex++] = pool[read++];
      } else {
        out[outIndex++] = value;
      }
    }
    return out;
  }

  // ../unison-sources/node_modules/entities/dist/generated/decode-data-html.js
  var htmlDecodeTree = /* @__PURE__ */ decodeTrieDict("!}.&u%}'&}*'~!6*)%&,~!J~!J~%L~y<~!R,~~%Lu~~#GD~~#|)1#%}^%}2%+#.##%##%}&%##%'#%##&%#%#'%#&#%#&#'#%%#&#%##%#)%''%&%#%#'%#%%#%%}%%%#%#&(23#%%#&-%0%('1#(##%#'##+%'*.:1}#%#6-+(%'%%#%%%}#L'2351&('%}&/N'(0(/*-%(%%}#'+&T%7.2}#&%&#%#36/5##%&%%#&#%%#))2%%##%&&'0~!#*+&'%1~!%).'3q?&%'1~!.##%6(~!+%%%(Gw'rT~!E#<nA%#jZ~!H%(~!42##~!*31&~!G%U~#)5~#`3~!J~!Z~%]~%Y~%C~!q~!u~#kz~%#~!6'~!D~!U~!?~#T~!c%~!G#'~%7|~!G~!J~!G&~#pb~(Df}#%}*&}#%##%##%##&#-}&'#'&%#.++}%mI,#,@&(}*%}*'%&##&#%##%}&0}#.},U},%}+%}&%}#%##&}B%(}(%}+%)})%##%#&}&%##%&}<%}>%#%&}*%}(%}9%}/%})%}*%}*%}?&}&%}3%}&*#%})%#%#)}#&#-#+*%E%%'%'#%}#*V##&##I}#&&##%&%#&&Qf%%))w/0+&%#(#.%-''''++++7}>%4'',##1,#%#&%##&#'##&#*#9)%&%}#*}%,#+P(%A&%#'&##wSD',9E00#y#@}(+}&%&>~!#~!X}#*}(&&}(&}(,%}%&#+&}#&}I%#%}%)#(},'%#*}4%%#%}(''}#/##(##),%-##%%)#&}(.}&%#&}%%}*&#%},&&}&%}#%*'#%})%}D&}&%}-&}6&#&}-,%}#%})-(~+`~,=?~I9'9%~!,#%})%})%}@%}?%}(~!?~#<~#pP~#BG~#=1#%K+~#?#~%;)~#A~#mF1~#A'~'X%'~#lR~#N~'N~#r~#m#-~#i'?%#'%~#B%##%,%#~#_%#0%~#]732~,w~2+#:&#%&'0%&>%}#>##F+)#%&&#(+_}4&}-%}(&}@&}O7Fdf0@+/v4}&WU##&/0#&'('B#%}.%}'+#%}#%%&#&%#%##+#&#)#6#'#.},%}c%},%#%##%&#&%#&~#>'*-.%##%##%}#%%}%'~#)D1}#%*&~#_%%'(~#S2%'.}#~#=##*'*-%}&'%'##&&~'E%.#&~#M4}%%##&'%#~#O1##%&#'+~#<B%##%%'%+~#;#@%}#&%#&&%#(~#H1}'%'##&&~#?A}&'~#D#%32}'&&&&~#[}'(#%}'~#;C})&}%%#%~#=&%,3}%'(#%%~#^'#&&)#%'~#Y%-~#d-%'~#^%%&#&&&}#~#b~2t*&'~&(~&@~0%~e~3}%*''0})&}+~!9##-}#%-hD*)1fC#%/&/fB#40~!+#)*4~!+~!K'&:~!/*7~!.#~!H~!L':~%x&~!H#~!*~%1~!I#~!+A~#p'~!F~~#-#~,,(~.Z~!V~%;'B'mq-W~!N~%I%#&&#&}#%},%%}'%}+X#%}#&}(%}'%}<%}#%}%%'}'%}:~![)9@~%>~#UA%-%##&~!C%~!-.9:~!1~!-^2/:a~!y,D*J#-5)/4~%23,~#G~!L1~!0X3`~!2+~!!0-~&E~!W~!o,>Y&]~%cZx_&~#O*9#A#'#+I'%#)~!0B*-5A+-((F&*M#)(-7-5+'-3a5Vi~!Y~!?+[)%3),ERHm~!+:D,VG.+)?fB%%*(%)'(#&80%1'8`K8?`+'Z#&O&'H5#*9)A%%5&3))0%39+.*7#()&&*=4@**L)<'_&*+..;(#*+)./&0#3)%')-8(4ixD(&.}%,('aI:,)%,k2231T)I'#/-W7,/'Q#.'Y24+h')37</31&83##&0#),H(?'&?/1##%#&&#%''-%&&&#(&''&#.-'%#%%(,')*'&#&#'##%(%(#%('#&##%%%%('%#%#%%#%#&%##h>w+v<ayvyvcg.uuhKr}g/v|g>u9i[~>g5uI~=RvdwEg;v/g;uk!!TTSx]@RT!U!#!@VBRUU!'UTe-d0c`e&gSdicedFcrdTaqb.kYcAohdYd@a3e+d}dMdtd.aJ#bqcK`dle/e.e'dwdPdodddjbEb}ogd^ofdpduc6j?l%d{drdqc)d7bacOdQ%T#Y)X.sR[yH>6Vyv3[xwLu>vo'!*.[yBacahoj>6Rew3[xqdZa#!a&#^(X-[yG>6Vyu3[xvg3sEr|g.u/Ri9db0T#^(Xa)!-[y;>6Vylg4wKs{JwNZt3@3r=c4Z([xlg;wKt!cpq's@v7A'*a(a+!-a#[y<3Dt?3Dt'>6Vym3[xmg9rxsNJwLZt4~?r?db1T#`-!(Xa,!0[yS>6Vz%NuQs.g4wKtnJwNZtS@3r>c4Z([y%g;wKtrdga8!a(!#&T*Y-Xa#!a0<or[yc3Dtq>6Vz43[y3JwNZtf@3s!Ju}!%Dti:pm3c_%X#tjB5pkd6q!r]u?voC'*-a.a2!0a&a+[yI3DtI3Ds~3DtH>6Vyw3[xx;:s#~<5pKJwNZtE@3r~d`a)!a2T#a.(!+U.X1[yT3Dt`3Dtv>6Vz&3[y&g9rxwzcxstPu.<rAJwLZtT~?r@dZa%!a.&^*Za(/Reu[ya>6Vz23[y1g3sEr}wkg{NuQRg{ci(U#5@b`~,cg#U(2WnH5wugcRh7dX#T(Y,a'Ta!!a,[yZ<]mj>6Vz,3[y+Pv#5ReZKu+=,%!H}7ABwkaS?Rh:BcW(X#<]mrj:ubv/ARekdg%!(!a.*Ta(Y.X1!#sP>Rl*Dt6[y>>6Vyo3Wf*jOvuumvuRgRJuq*!:9<B@bX~3jVv&v@s@5Re[d/rQt{uAvo&a&a*)a2!,0Wf!3Dt0=Bs'>6Re}3[xy~<5s%JwJZt1~Gs)c;&!#2sJkNuXvzq7rxu,Re8dka4!a8(aEZ+a@Y.X1Xa)[yd=Bs(3DtP>6Vz53[y4cX#X&Re:avRe9~<5s&JwJZtQ~Gs*i^rzvdRg+Jv{%!2sbB@bX}kdga,!Za?&^*T1/!a'Dt+[y6>6Vyf3Wf%g/u;s4hGu6?Rh-JvZ,!c%#&RoX54Rivj7uyvf8RgTKvZB%*!2sGh<vu5Rgq<=C::9bb~#dZ#T&Ta6Y.X*Dt>[y93Wf)coZ(T,6VyifluvRgC@95@B@bX~/hFu34cC#T,k/unq8w8Q5RkUklwQuzunq8w8Q5Rk8d/rJu?v8w9)-&!a0a;a&aIWejg3sEr/h1s<DtDJvyZqY5aws3Jvy!&Wei~Hr1:au5@Bag>23E~5c:Z&bX};kKv?w&unuVu5Rjc;>bs)#~@:Rh.=ay<a]C;b`}Vd6s/t{uAvoaxa()!a,a7%-a#a2Dt,[yF2Wo[>6Vyt3[xuNuPRi&NuPwpi#RoWh?vf8Ri%Jv]!%Ri:KvxD!.'2WeAjZu`q9rxu,Re7woeAg-unLq(qA_/*2Wg_g3u5q^9:4E}/jTrxrzv=Wkkd~0UX#^^Xa-a1a5T&a=U1a'*aEa]!a*aPaA-adok[y54Rn>;:p3~Dp5g9rpsFNvZqjg3uJp4~<5p0Pw;5qlJwNZt*@3p1Pw:5p/Ou!5p2JvG'!6Vye=<qnJvh_[xhg3v,Rh3kOwOw-sDuev/Re^dha[a%!%!a+#Ta7)-5TaCaO!aka!a)sf[yb2>Rl!9ARiq5E}Qg=ucRkBE|oJrJ_@Wk~@Wk{JrJ_@Wk|@WkyJrJ_@Wk}@WkzJvO_[y2g-vMRmiKuYC!)&>Ri;>Ri<@3RkNc](X#@9Rk=g5vuRmhKvDB!+'=]meg3u4Rmgd)#Y'Vz3CARmfd`a+!%T'!+#Ta1Ta6TaM-sTDt9[yA9sYd'%Y#s[[xpj:ueunaXRgEjRq,v-vuqdd2'`#6Rev<32@5>:2<E}5xIo9a*X#Y(;5RePJvD_g>vyRgNj8w)v8<wggs:RgXiZt|vjx,hSq3ah!-(~@:Ro/Ou!5RhWj^v(pyw8unRhUdx-UY#^Ua.a3a70!)%UX1TaDa)'omRiRRhE[y:3Dsz=Br,>6Vyj3[xkg6ruwjcqsrPw;5r*Ku]D'Zt-@3r(~?r.i[vwv]dU1a--U#`a4(g/vsRhPOu!5RhLj:rmu9Wo!~@:wdh@g/vsRiTjXuvvNr}:RhBj^v(pyw8unRn]dz1UYa'a+^Y(!aETZalaRY.Ta?a4[yDJw1!#qLsW>6Vyrfzq-pLflpwRe|Js>%!Dt@3Dt&Jvy_[xs~HrnjMuwpsw'RecKu+D#'!t<~Grl~?rjg5u-x,gwp{ah!-(~@:Rg~Ou!5Rh'jXuvvNr}:Rh#cW#X/c;&!#2sLi[v7u7RgpJv)(!iLrxu,Re6j7v@s@5Se[e7d`aW!Za(a`T.a#!a3!&aDa-!9)Dt_=6s+3[x~~DR|h~DS6avhGun5RkZj3w)v-]mkKunB!&*]kb97R|i<ARk<c:Z(6Vy}Juh'!wziMRoS:F|vkLuauJv5vtvQRh1d='T+Y#VyO~DR|jcF#T'7R|g97R|kJv3'!ay<Rj,Jvh&!:ReXcsa6*a+#a#_aIRf9aLRf?c,Z&Rf5Rf7c.Z&Rf;Rf>cQ#%T'p-Rf8Rf=ct#%'(*!,p,Rf4p+Rf6Rf:Rf<d~'Ua%U*^UYa(!a,-!#a4YaTalaEX0a8a<Weo3Dt/3Dsx=Br93Wen~Dr;~<5p<JwNZt2@3p=Pw:5p;Ou!5r3c7&!#:p>3Ds}KvGB)_6Vyk2sM=<r7x'eovA(!hFu1ARf}cV#X&@r5j6rvwQa^Rf3c=Za'wkghJv__g;unRggA53B9=b^}%j6uduo5Jq;!(hIv%2Re`Ou4ARe_e%a#^^^Xa&!a*a2!&a6YaP!*ad!#a:aE/5Rn?[y@>6Vyp;:pE~DrY~<5pBJwNZt8@3pCh=rt3rWPw:5pAJup_[xoNuPpF9c!#'45pD5ARn)d8#X'X*3@rU72s]h>v<<sSjJpqvewOJq/(!hNw'5ReBk0s2u3w/w'5ReE5@Jq.!a+JQ!&WeU23d(#Y&RjG5]jBk!u7w&u0udARjEe#+^^^Ub#!a2/a`Z(agT1!a-a;|@TaG!aS[yV=Re~fow'RguNuPRe?bz#'>RoUWeL>:Cbb|?JwPZtVg6ruRmzJvD'!6Vz(g/vmRh~Jvy_[y(g9voRgyx*cy(#2>Ri2B9b]~9kIw9u7rluJu3Rg]dI#a%UY'@=p%CAx.gQZ&RhwwygtRm{x5g_Z'+ABqR9Woa=Bp&dV#^*Xa'!&@o{g4v]Rk;Jv{!%Rk[wkkiA5RkiwwfUB=x,fUuqC&*!>RfTg8v0RfV~ARfSd;rJsAuAv9wR'ae+/aO!a@aza/a#[yQ@Wg!2Wemg3sEr0JvB_g>uvReWg2v+Re=KupB_+[y!2AbY~-~Hr2AJwD!(h<~El>h<~El?Kun@+_:9b`}Kg-v/Ri3g;vtwyk_9]k_d=&T#*U.6qh@Ab`|K9:H|CJv[!&3Dtex'fDwC%!Rf[9WlMd[(^X,!a%Z06Vz!@WgBg=v~Rgvg,QRe@awd,#Y+jTv|Q~EfWj]uNr|~FRfXdy#Y&^Ua%!aO.!(a)Ua;=!a@aKap!a-,a!Ta]a[rSa]p?[y82sK=Bq~;:p:~<5p8Pw:5p7d'#Y'Wf(;RnRi[u4w&RgJJvG'!6Vyh=<r#ijuuv/sIKuYD'ZtG@3p9~Gr&d2#`(g<vtRgFj`u5w&rqpxRf2CJuY!+:wfnTOu!5Rg}jNs1ucv&RfwJvA!&3@q|BDcC#T,k/unq8w8Q5RkTklwQuzunq8w8Q5Rk9dga#!a'!a=#a0!:+Tb*b@aO.a4!aba8aFJv^}?!VyR~Dr<g;u%Rn.~<5p[x'e`wNZtR@3p]Pw:5pZhNvjBp.woe_g5u-r4JwF!%DtO3:ooc7&!#:p^3DtpLuGw(!+%)Dtk6Vz#2sd=<r8d'#Y([y#<x3gJt`w@!)%}MRiowzikRij=]ilxAf3,U(#B2Rf#g0v-Rm[ck{`U#]giKv3>)!&6Ri154s,KuGB_%@r68r:dJ|t`#X(9<E|u2@H|rx3gJu?w'!+'1Nu7Reg4=H~+9<wxgY95Rm]xLggZ-`(X}U2:Ri4h<uOawRmsJv__5@bb{jbV~3dka#a'a]!,#a+U=a>b6a3b%!/aKa/)!arwve^VyJ;:pR~DpTg3uJpS~<5pOPw;5qmPw:5pNOu!5pQJvG'!6Vyx=<qoJvA!{~Jup!%@qk7Rn/KvyD!}''[xz;>wkh'?Rh,x8gyt`w5D!&),(SgyccRgztJ@3pPB5p#d'(Y#<]mmifubw&RgoJvE&!82s^JvF&!8Rf,ADb]~;x=h'rNu]vK!,%'*0RnORh)4Rh*AqQg-vaRnNg;wHwkh'ba~4cE#Ta*x3gctyw@'!+%RnFRnD<4Rn@hFvK5RnCxWg[#`&a0Ua()`1Rm75Rg[c]%X#qi8Rg^NvdRj>BwzgZauwji7Rm6A4wgg]d1#&(*,.0a#Rm;Rm<Rm=Rm>Rm?Rm@RmARmBe%#^^^Xaea?aC/b+(,!a+a#!a/!>a&Ta<aKbD!2wphBRnk[yPw}hE|.=Br-3Dtm>6Vy~g6urRf.x,hPrNav!%'RnqRo%Ro#Nu;q[Pw;5r+JwNZtM@3r)d'#Y'Weh;xChL#`&RnmRnoKu}>%(!Rne~Bs-;2wjcussJv+'!aYSO}6@B<5?ba~8LrNvj!.%*ROwungw~ng~:9;Ri^>wtnig;wHRnixDh@|(UZ.x1h@|)!#:2<H|*xHn]#-UX'3Ro)z=iT}6ARns=Bwsn_wpnaRncw]aR(#UXa&Ua*a/=]iPd'#Y&Ro'WnXf{QRm2hNvj]nZd`'T~&1`{|`#9b]{}c:'!#Wl{>@=be}]?cl{{U#:5Abb}Jds#^YaF!a*b4a#a3aPa>&Tb!bH!*a_!Eau?/a&RjY<]gj>6Vz*;:pe~DrZg,QRj1JwNZtX@wihspcJvZ&!VyX9WmOJu|!|N2WmHJvh&!]ht~Bpbcn&T(!#RmQ<s7Nu;padH#X'`+WmJ@>RmKCARhnKup=!)&Wf+:RhqNuPpf9c!#'45pd5AwghpARn(Ls@w!%,)!RmP@Wfe<E|IJva!&WmNg8vsRmLd`*.`#Y'Xa!axRn*]hrA8Rhug5s@rXg8u!RmMd8#X'X*3@rV72smdI*#UY&RmICARho~GsgxVgd)Ta'U-Y&Xa!T#RnEWnA@Wffg1uDRi0hFvK5RnBxGnG&#`%owp)@wsf+bX}Ze-*1!a*^^^Ua|!#a.aq&Ya2!a>.a6!a:aO`aJDtL[y`@Wg#>6Vz12@wzoYRoZNuPRi!NuPRhzg=ucRi,@=b`{Yg=ucRi-ACJvB!&Sh[ebSh]ebi`wUuFRm4Jw2_[y0JvB!.<Ju(!&SoG}6Shd}6<Ju(!&SoH}6She}6Kur@._g5vHRieJvx!{L2G{Kx6gd'T#?Rh82Wi5cZ#X(g1w)Rm5dW-Y(Ta#!a)!#aYa=wnfE=su2>>bU{0j9udv:<svj8uQv-7RgHdE%#^'sq9sp=>Bb_{TJv`!&g/r|snj6v(us5d,#Y(56H}[978H}]Jw5!&g1rushJvB!+j;v{u5?zDhd}6}bj;v{u5?zDhe}6}ce*#`(^^^a[aea!=!a6a*aoXb1a.!aAbL!b>,b'aL!aV@Wf|2Wlg3[y/JwNZt^@3piPw:5pgJunZou3@rsJva&!Vy_g<v~Rm#JvG'!6Vz0=<r{Ju{%!:pj@WfsiXuJu3Rm:JvZ&!WfA~Bph@c4Z&Dtwax5rubx(#:awRk1@d,#Y&RfjRfid1#,Y(@Wfp2Wlrg5s@ryKu[@!,'=]ig9wlk?Rk>g5u-rqJvy'!@9RkQcH(T#=>Ri~@<wkj(Wj(KuZB*!&<7rw@9RkRcH(T#=>Ri}@<wkj)Wj)dg(Ta2Xa9X#`-!a*CARhg@@=I}d9x;c~#X%so=<sj>2@@=aybb}XjWv0Q~EfEj3vLv;<d,#Y(56H}`978H}_dgaPaFa'a/!#a3Y0a_a;a|!1(a7-[yE3[xt;:pJNvZrrg3uJrvJwNZt=@3pIh=rt3rxPw:5pGOu!5rpJvG'!6Vys=<rz@c4Z&Dt(ax5rtJvZ!&~BpH@wsfNg-vaRlNci*U#=<wei<F}a5@Jq.!a*JQ!%@qZ23d(#Y&RjH5]jCk!u7w&u0udARjFd/prq=tyvpaEa(a:.!a1aZ(@@=I}:9wpd%=<sX55w_h}@@=I{t=ay<aU@@=I}T=ay<2@@=I})?C9:9au@9Cb]}DP~=x-fAZ(2Wl1=ay<aU@@=I}>5@d##Y+jTv|vV~EfFj]uNpn~FRfGdgaK!Z2&!a8a-Tb({E!acTbM*!a(DtY[yYd'%Y#sl[y*hHvh>Re5x2c{Z}.j4uCvcawRiMd+#X+_x&d!},<5RkX;2Hzw@x,gavfB-!{CcF&T#Roe;RodwWbBg5urRgaKvHC*_6Vz+<4opieuew&Rmq@d]&Y)X,T#X0Rh}<BqP=4qS9:ReMg/ujReNJw0!/<Jui%!bd{kawwnemRelAxUa?a3#*.&UX(Ya+a/RhvRnQ<o}9Wmtd-#Y&RgSRmw9;Rmxay=Rmyg-vaRmuxEhSrNu,v-voC!%(aR.a(a7+1Ro1>Ro5CE{A9b]{@;5x#eO{:g;urRi+KrNA!%(Ro3>Ro79;Ri_Ku@>{;&!x%gX|{KunA_+g5QRj/g3u5Rj#g>uERj%wio/xRhS&!,!#^1U}wba{8>>@=be}qC@:D5ba{7Ku+A&!}x?ba}t>>@=be}se(aA^^^Uat!b0#{pa+awUazbGa#aLb9bgaWac'a5TbS=Br!d1#`%scp_Jvl!#rT>Re0JvX&!VyN=H{Fcm#U&:pY=ReaJv2&!]h0=]nUJvG'!6Vy|=<r%JrM_=]h2@Wlud'#)U'Wf'b]{i=]h/Jvh!&~BpWg=v]RnMx+ny#'Nu;pVwjnu=]nwxJnx,T#`&Reqwjnt=]nvieu9vrRjLLuYwP(#+!th@wih5pX~Gr'g5v/Rh4KunA'!-CARnP@wwiN:Rm_9x'cvw>!|l=<saKvAA!0&3@q}>w^e1bp#&Re2Re3BDx7gH#T|f5H|eKuZ>!%(:qNAH{]Jv6!+3B2B9=b^{X<5<B92:E{ZLvhwA(a;a%!igQuyRmad+#Y}m@3Rh5d8#X'X*:AqUAHzmaxwbh<aXRnVcF}RT#Nw&cj#U(BWnug/vsRntdka)(a3+.Zb7aYYan1!bVa@Xa}[y^@b[{G=H{+hFu73Rj&Pv#5ReQcK%T#sig1v{Rj'Ku+D#'!t]~Grm~?rkKuMB!01d5#`'Vy.ta3Dtu~Hroc8#'{^45s85AwZbP&!#Rn!wghxWn#KvEA!)&2RlA2RlBx:h|#(T,=]j09Wobz>x]z/@awRoTd+#Y(az]hFhCrm4d,#Y+jTv|Q~EfMj]uNr|~FRfOdCa!Xa9_X#@<plJvf!%b`{(9;Rgwc;.!#2x7cw#T|UDb]|T5Ju={(!=@E{&Jv)&!Ab`{'awJvf!~*>>@=be{#KuY>!+&4Ezyi[ugv&RjIdea+T)#UXa&T-T&a!Rh9auRmW=]kLg5vuRn+g3u4Rn-Ow6ARn,hHus5xNk?#UX(U~)/g8v0RkD~AwkkF?Ri.OuNBwkkA?Ri/d|a2`a*^UYa.!aBTZaTa'Xa;!(!2!-a#b2[yC>6Vyq3[xr2Wi?g1rusVh%s?DtF~<5rbJs;%!DtBfswKtCj[uvuSsEu3RgVx3o:u+wN'*Zt;@3rd~Grh~?rfg8w)Lq)qE&-a%!>bI|`jWv0vV~EfCjTv|vV~Ef@j]uNpn~FRfBcK#T']gWNu7x,k7q4ai(0!hHv8<RhmkMu9vrsBuev/RhlCJvB!,g<v{wchh~@:Rhji[vrv{wchi~@:RhkdS&a5UY#Ta!RgPwwiI5BwciI~@:Rh`x'iJvj'!5]iJPu8Bwch]~@:Rhach)U#h3rp]gLh@t|Ax,hTq3ah!-(~@:Ro0Ou!5RhXj^v(pyw8unRhVd|)`,^UYas!a?/a2Z'a^Ta{Tb7Ta(a#!a,Wf&9sZ3DtAadamov=Bqt3[xig8vsRm~>waiL2b`{QJv*_Ouv2qgj<v]v2BqfdR'X*X#Y-@3qr~Gqv~?p6hHv-]glPup5Lq+q?_%*b_{qF{n9b^{rOu4ARhpKvCD!+&~Bqp:5Dbb}nwoiKl&unuTuBv]v+ueunaXRf0=Jvh!0nKufu8v1w&w7q%w&uHrz:Rgnj5w,uxDJq/(!hNw'5ReCk0s2u3w/w'5ReFd>Za&!*UaA=<wkgsRnSJv^!%Refifw3vyRgOKu_B'!,<]gkiiu:w&Rh<=C@a^<B57@2F{[<B5@aW:=3away9A5aW=<B=C@a^<B57@2F{Ie-#`(^^^bCara.b8aza6!/bZ,!adTbnTbOb+aFaS!aAT9@Wf~2Wli3Dtl2@d,#Y&RfnRfmJwJZtN~GqyJva&!VyMg<v~Rm%iXuJu3Rm9Jv[_=]ih9wlkDRkCd1#`(@Wg>2Wls3cH#T(@<Rj*=>Ri|b~'#23s9h<~El.d'#Y&Dtxi^rzvdRl#d*#U%(o|B2s`hJwSaxRmDKv4B&!1:Rmdd5#`'Vx}to~Hq{x'f1v3(!BA5ba|bJv_&!Wfug1v]ReIdO+U/Y#&G}-8wze=Rh{g1v]ReHg/uQRf/by#)ibQwERl/cH#T(@<Rj+=>Ri{cNu+vlax-!(#a0qa9<Rii2;;bU{H;x<i=&X#Rk`<4wwi=C9H~8xAI(Y#<azRi@45wXI<B9;5bb~7dL(X#Xa(+!aL6Vy{g5QqOau:5au2@ay547EzbxOcU(UX-T#Ta#:Cbb|A?wjh/b_|SOw6ARgtihr}u7Rhy<d1#T)X1@@=I|~=ay<2@@=aybb}Sj3vLv;<d,#Y(56H}A978H}@dGpvs@uAu`vcw9*!aFa+ai%(b!aXa8.a?a[ozWey=sU2@G}Nch&U#Rf_WexKu+D#'!t:~Gr`~?r^j]uNr|~FRg*j^psurwJt|RmcKv)@&!)7Rkv~Br[@wxfO:Rl3co#U'6Rezj_q#vIuavjRltwzeyh@vr5JqD0!>aY?C9:9au@9Cb]}9cl#U*5;5<H||jbuus1ucv&Rfvg1v~d/pppzqFr^a--a~!aMat1(hFv;Wiz@@=Izoj5uuv-7Rix~Cw`fk2WlVcZ#X,k)u3vWs@u2]ktg;wEx'fBq(_2Wg/jTv|vV~EfoJv]!15x'hzqG!(P~EfU~CRl_j6v(us5x4i-#T(2WmZ?C2F|d>Kq<aj1!*jTqIsBv=Wl`~Cw`fi2WlWj`v0u*~>RlR=c>Z,k#u3vWs@u2]kr<c1Z+jTqIsBv=Wla~Cw`fm2WlXdmb3!a{(arZa`bkTa%TbQTa-a9+c'!aM!/[yL=Bqug.w'RifhFvyDRj.g>vgwyk^9]k^Jv3_@WfbAARkhJw2_[x|JvB_wkoIRoKwkoJRoLd'(Y#<]gm=<9<H|yd'%_X#skDtb3awwqkgNulRkgdB#^',9:p'hJwSaxRmEBwVb8@4=H|qLu+w50&!)@3qs~?pU>Awwn;;Rn=c:Z'ARn<=<qwKvC@!/&~BqqJv6!&]eVb^z^xRge'/a%+^`#Sge}6<4Rn3=]n0Pw2>Rn8Jw0!&>Rn:>Rn6cY#a7+!a&=<wkaNw~h3z_c5Z{=wjh#=]nLKv^D!&)Vyz=bW|swYb<WetcG#T(2wxa@qVx@gD#Y&b^|V5JwG&!5bb|pg/w&RgD@x=kHs=uAvn!a%%/'+RmSRh694Ro`g-vaRmRhHv-]mlxCcS#`&ba~.5cD#Ta)P~=d,#Y(56H{>978H{Dd_#{2^Y%_+qbbb{6g3sERhsbU{?dfa.,`a(Xa<!aiX#(55RiG54RiHcI#T'WiU3RiVNvdwtfcRlKNvdd,#Y&RlHRlExQgf.1*^T'X#Sgf}6Wn4=]hfPrk>Rn7Jw0!&>Rn5>Rn9Lunw?&a2!,5<oq@@wqfdRlJj5Q~=d,#Y(~ARfcOuN]fdDKw;ay(}i!547E}j?cI#T(@5bV}iCbV}hdv(^^Tb?a40,b##Tbo!a*bR!a<b|a/!aKai!aU[yK=]o^g:v>ReGJwPZtK<7Rh+h<~El,Pv#5ReR@awwxjCg,ulRjDJv6&!]j!z?aQeeg>w=Sh<eeJw;!&axEzOg,Qosc!#*:wkeJ]eJ>x'h-u(!%Ro.w~h.zPdNZ(X,Ya![x{;9ReY;wkgxRiF:x?ap#Y&RmUg<s2Rkod]+UY0TZ'!a&A9sw<=bczLNvuw{gqzNhJwSaxRmCKuLay!#&s_Rf-55b^{uJvZa!!c%#(55Ri654wmiu5RiuawLu,vp!+}^%b_}Y9;wkgxba}o>A9:=b^}zKuh=a''!3awRk3c*'!#aHRk6c+Z&Rk5Rk4Jv)&!awRjSawd9*`#0?C2@EzMj8u<uJ5RmbjQrquJu3x,k>uq@_+=ayb^|W~ARkEOuN]k@7dhzV^X/X&a-#zRzSb`zXcJzTT#2WkVKvDBzW!%FzY9;5bbzWjQrquJu3Jw3%!b`zU=ayb^zQd:#X(T-a!6Vyywxh}=b]{Jg=u1RiAdGp~qHtzv!w(wA+a+a;<!aJaYai'anasb(=azRmV:Cbb{MLq2vb!%')RjuRjrRjtRjqx3jnqCw3!%')Rk(Rk+Rk&Rk)Lq2vb!%')Rj{RjxRjzRjwLq2vb!%')RjsRjpRjfRjex3jcqCw3!%')Rk'Rk*RjkRjl9<CbbzfOu4ARhxLq2vb!%')RjyRjvRjhRjgx=joq*uKvb!%')+-Rk.Rk%Rj~Rk-Rk#Rj}x=jdq*uKvb!%')+-Rk,Rk!Rj|RjmRjjRjidAq&qKs@uAv8Aa.'*-a@a&0!aM@a5[y73Dsy3Ds|3Dt):wxgI2sHJwJZt.~Gqxwsf0ikrzt}Rl0Jvy_[xj~HqzKv_A|D!&WfP8axRoVcf,U#k(v]v+ueunaXRf1Ju}'!g8u#Ri=jQw!sCunLprq>!,')~<5qeGzq9F{W=c##%s5au:5aU3CBE|;d4#X(D!a&6Vygx(b;#(=]ed?C2F{N<capoq2r[a&!aPa9,'Pw;5s:@@=I|,55w_h|@@=IzcP~=x'fCqB_2Wl2>aU@@=I|1OuNBc1Z+jTqIsBv=Wlc~Cw`fl2WlZ~AcTa%!Z+jTqIsBv=Wlb~Cw`fh2WlYk+uNqJsBv=WlSg,u3dca3#UXaMYa)TaB-=cM|7T#<bI}l5@B932:aV2G{BOuNBJq:|M!5Ezt=<B=C@a^<B57@2F{v>cB{/T#=ay<bI{3Jv6!a.6BKq0ah&+!5E}HP~Ef{978BaU@@=Iza<7d#.Y#978BaU@@=IzH~AJq0!(@@=IzG978BaU@@=IzFe,aU*Y&^^^bvJb,b:bFad!a,c2Ta>aL.bo6!a#CbTa'T#Re{2Wlh2@G{yg6t~Ro_NvdRfticuRQRllJv3&!x&c|zs@Jw3!%RflwpfkRlpKuL;%(!Re<@G|C2GzdhIvuBwgjAg-u0RjAKQB%!(GzZ@G|5NuuRl7d='T+Y#Vy[g<v~Rm!==G|>JvA!)@wma=]m1ifuaw&RmnLs@vT'!|/+[y,g:v>ReTJw1!#qX=x!eC{bLu+wT&)ZtZauq_~Graci&U#F|89:r_Lupvq!.)&2RlG8RfaC=x!eF{_h?rpWlmd&'!#X|&]k::xJey#`'T|+<E|&2@H|%dE#(^,g;u.RiEg6vjRiC9xCkA{O|zY#g=ucRmXKs0@!&*@G|m@awRknJuh!,3d(}gY}eJvj!%Rm):Jw3!%Rm+Rm-Ls0w(&!a(a#@b[|6cZ#X'7RkxWgAOu4ARn'dH'U#Y*Vz-Wm'CARm}d]*#a%^a*T'aK!a<9bV{PC=p*Jw4!&SgxcbB5r]idw(wBRmF7xFkt#&`(Rm/Rm8E|!JuY_9:Rl5=wrgr2:bbxd@xXfB(a*#T+!.X0X1Ta/a'T&RlDRfL>RlyARl9b[z[>RfZ:RlL:RfRwlg/ARl;9;RlxKv,A/!%7s69<74=BA5ba{-8Bde#`a<XaKYa1,a'P~=wxfB2bZ}}?C972@@=I}r8@55B9;5bb}G978B2@@=aybb}3j3vLv;<Jw3&!>Rfk=ayb^}4~Ad1#`*@@=aybb{w2@>==<bbz]dx+UY#^UaF!a9!bB'Ya1.!ajXa#%olRhD[y=3Dt#Ov5BrHKuMB%!(Rf^Wep~HrJwkiQjKr|~FRg)Ku+D#'!t5~GrF~?rDdV)UY,Z/_7RkuG{<~BrBg,rlsO:235B@bX}|d?a1!#`(6Vyn5@d##Y+jTv|vV~EfIj]uNpn~FRfH7Lq2vb1!a9-978BaU@@=Iz9978BbU}#~AJq0!(@@=Iz8978BaU@@=Iz7~AJQ|}!978BbU}!JvkaK!AdUa21-U#`a+(g/vsRn~Ou!5RPj:rmu9WhOjXuvvNr}:RhAj^v(pyw8unRn[kPr}p|u7vwv]RiSBd;pppzq@qHQa?(b.!a.a`@.|xa(hFv;Wiyj5uuv-7Riw~Cw`fg2WlU978BbU|wOuNBJqG!(P~EfD~CRlQcZ#X,k)u3vWs@u2]ksg;wEx'f@q1_2Wg.j]uNpn~FRfqJv]!15x'h{qG!(@@=IzK~CRl^j6v(us5x4i,#T(2WmY?C2F{1>Kq<aj1!*jTqIsBv=Wld~Cw`fj2Wl[j`v0u*~>RlT=c>Z,k#u3vWs@u2]kq<c1Z+jTqIsBv=Wle~Cw`fn2Wl]dn1#c(a(b^a2!b/bAT(bj!aDa7bu,a_a{c0!2T0g:v>ReD2@G{42@G{5~DpM~<5rc=Bx6i>{RT#RnI@zCx]y]z:2Jv[!zr5Awyk]9]k]dD(Y+X#6Vz.g=wKtgwhaCwgmTWj2Lu,w%_+/[y-B;b^xeg3u3Rj-2@bX{*KrJ<!+'@Wg(g?QRlC@Jv`!%b[zIwsfII}8JQ_@w|kW|=Jv(%!AqcOuNBJvEzh!bYzjLs@wP#(0!oy@>RkdJwMZtc3Dtd@BcG#T'9bWxg2@2Fznd*#Y+;2x'c}w<zizixNgwa#Z'U+!/!a'!a+w~g~z6wcn{Rn}wcnzRn|5Rh%=]nJg5vuRmvNvdRlvcprJu}w*az*a#!%.a.'Bot9qT]kj@Wg'ay2Gzv@Jv`!%b[zEwsfHI}1;ck#Ux`<Cbbx_Lu+w!a&0*!wko*wwo,So,}6Juqxf!E}PigQuyRm`d3(`#8>Rn%:A5B;bZ~%KvhCa!a2!x>k7#Uxb@b{#xaRk7Jw0!)>wwhlShl}6>wwhmShm}6CJvB!.x'hhvj{!!5Bwkhhbaz}x'hivjz~!5Bwkhibaz|xEhTrNu,v-vpD!a%&/)a3a.,%Ro2t[CE{)@3re9b]{%wjo09:rgc:Z&Ro6=<riifuaw&RmoKrNA!%(Ro4>Ro89;Ri`dSaL'UYzxZb)7Rka3xRhT&!,!#^1U}vbaz{>>@=be}yC@:D5bazzKu+A&!}{?ba}y>>@=be}wxBh[t`u~vJvr!%a!a()a,a0a4RoC=]o;Ju(!%RoGRhdwjh`=]oAg>w#Ro?g5vuRo=NvdRl|Ku]C.!&;RoEJvB!%RoORoMBx'h[v+_?w~h`}~5?w~hd~!xKh]oiptu-utv.vp!#%&a30a@a'a+(a/aOp(o~p!RoDJu(!%RoHRhewjha=]oBNvdRl}g>w#Ro@g5vuRo>c[#X']o<CauRoRAd-#Y':RkpauRoQKu]C.!&;RoFJvB!%RoNRoPBx'h]v+_?w~ha}t5?w~he}ue!/UbhYacXaW^Tc&a;b:a-c/#b&aja1(!cL+!bKbt!bmcRc9aIc?8[yW3Dtt94Rg`Jv}!&SiRMzBhEebShEMNuPRe>x7gL#TzuwjirRipc<Z&>on;>z=h-MSh.Mwqczx'a7vj&!>Re4@=ResJt__NuPRi*NuPRi)j]uNr|~FRfzKrJ>_+@Wfy@Wf]2WocKrJ<!+'@Wg%g/QRl@@Jv`!&awRl<wsfFIzgLu(w*!.*&ShBMwvhIRhI9;RhNx1hK'!#Sn]Mx1hK~0!#:2<H~7cNu+w7D*'1ZtW>Rn1~?rOc:Z&Rn2=<rQ<7wjh&=BSnLMc]#X(6Vz)w[b=a!U#9wzgMc3#&(RgMRitRis<x,gKt`ax!&+SioM=BSilMc3#&(RgKRinRimKurB,!&SiQMzBhDebShDM6BJQ!(P~Efx978B2@@=I}WLrJw!!,a*&@G}O@9wkibRid@@x'fKwC!&SlDMSfLMjUv~Q~EfKKv3@a+!(hFv-]mpx/hYZ(C5RiWz<o/MwkhY?So/M@x,gbvfB*&!SgEM:SoeeehFu3:Rgbda(,^TZa)X/7Sg[eb:2RgI~BrMC@wgkc:wwkcRerx3h(uUvK!&*,SnOM4Sh*MArRg;wHRh(x=h;rJvPwI!a4',a'0@Wg&=BSh/Mg>w=Rh=g3w*wwgGRgGcW(X#;Sg}M2Gzk@Jv`!&awRl=wsfGIz`dKZ*T'Y-:RhR7RhQg5u-p`j6v(us5d,#Y+~Awkia?RicOuNBwkibba}Ld6p~tyu_vbAa'a+!a/'a3aEa8a!>Sh,ebJv{!&Sh@ebSaReb9;SgwebNuPRi(NvdRl)NuPRi'hHu^<Rm^Jvv_@Wl(g;u1Si/ebKu'B&!*Sh?eb@Wl'z@aPeb95Si.ebcpputyvjB)!,&a+0a%ShAMWeK@G}C@WfJ9;RhMwvhH9w{ia}ix,hJvRA1(!zAn[MRhHx1hJ~*!#hFv(BSn[MBJQ!(@@=I~'978B2@@=I}2db.Ua<'X}+T#a0XaG2G}E;wkg|wuh!Rh!x,hZu,@)!&So0MVy)C5RiXACJvB!&5RiY5RiZg8w)cG}*T#2@bU}=KsA>(!a.3wkhZba~(x,h^u(A!&(SoCMRhb5Bz=h[eb?w~hb~6x,h_u(A!&(SoDMRhc5Bz=h]eb?w~hc~6e)aA1T#T,^^^c-bMb&blcPaP(a/!0!bA=b5c@a(!bfbrc#2afwmhARnjwchORnp2Wlf3DtsNvdRl-2@wpa<]m0bx(#:awRk2@Jw3!%RfhwpfgRlnKQB%!(G{V@G|'NuuRl6d='T+Y#VyUg<v~Rl~==G|<Jv+'!aYShC}6@B<5?ba~8@Jw3'!g2QRljhLrpWlOd+#Y'g.w'rIg>w*wgj@g-u0Rj@Lu+wT&)ZtUauq]~GrGci&U#F|39:rELrNvj!.%*RhCwunfw~nf~:9;Ri]>wtnhg;wHRnhx3hDs@v~!/+'@Wfr@9RkSNu&Rlo=@<5GzoKs0@_+@Wl+@awRkmJuh!-3d(}pY#qWJvj!%Rm(:Jw3!%Rm,Rm*de&!1U-U#`)Re;@G|.@9Ri82@wjfvRlq=@<5GzpLvOvr!).&2RlF8Rf`C=x!eE{.Jw3_g2QRlkhLrpWlPde(!#U{s,UXa*Ta'[y'g:v>ReS;x0PZ&RnlRnn~HrKJw1}f!=x!eB|2w]aP(#Xa&a*Ta.Ua2a7=]iOd'#Y&Ro&WnWg;u.RiDg6vjRiBNvdRlzhNvj]nYJuW_2Wm3x)kFze{9d])!a.!,Y01!#&aC!a3RndC=ox~BrC@2b^{pg,rlse7x'ksuq!%Rm.E{xidw(wBRmGx9o+)X#wwo-So-}69:Rl4@xSf@a#XZ'X)X,Ta(/ARl8b[xc>RfY:RlI:RfQwlg.ARl:9;Rlwdn'#^XafaQa1X1TaHTa)@b[{zcZ#X'7RkwWg@Ou4ARn&x)kG#{,g7u/RkGdH'U#Y*Vz'Wm&CARm|bx#(A]gUbUzJj9Q~=d,#Y(56H}l978H{U7d,0#U*2>ABb_xZ978BbU{e~AJQ{g!978BbU{hxMh?ad{oUYZ.x1h?{l!#:2<H{mx3n[t{vl!,&a%3Ro(z=iS}6ARnr=Bwsn^wvn`Rnbd`*T}B0!#^X'BG{c9b]{a>>@=be}F?JvS!&BG{d7BG}(Bde#`a1X,Ya@!a'P~=wxf@2bZ}I56B2@@=aybb}08@55B9;5bb}<j3vLv;<Jw3&!>Rfg=ayb^}&OuNBKuLA!)a!P~=x#fD{f2@>==<bbzl?C972@@=Ix^d6rSu,v7w*C(0a)a6#B+a%!sQ[y?3Dt%3[xn~<5rLOu!5p@Ku+D#'!t7~GrP~?rNKvlaya7'!h+v-5qMg=t|cd,U#5AAaa5Abb{S@52B5@a[@52B5Gx[iXueu;d<#`a(!/549C;ag>23ExY5@Dah89b^~689Jv)!~2b[~1Lv'w(%*!a#bX|aPrmawRe]keu7uhv-q6rxu,q`xTo]/a5aU!bNaDXbi!b-!ao!b<bwA!#5@B932:aV2G|:d-)Y#hJrL>RhG<7@C5<H|_=Cau:5aj5@B932:bJ|ng>vIbs)#?C2F|9jPv0w.vISh-MKvUaz(.!9ABbb|[5;5<H|Eg>unwfh;9:4E|YjQsBt|vjx'hYq3!(?C2F|J:2<BaY?C2F|GOu!5x,g|p{ah!-(?C2F|c9:4E|OjXuvvNr}:Rh&i[w*t|cd+U#jJvsu)vsSn~Mkfrmu9p}u7vwv]So!McW#Xa!ax5@A5aY:5;5<H|>kJv~vYrquJu3x4ib#T)2@SmZM?C2F|Bj:rmu9@xPhI(a*a#U#`a3-5Abb|L~@:RhK9:4E|0@52B5G|#C::aY?C2F|-:2<BaY?C2F|.5Jvk!a)javYrquJu3x4ia#T)2@SmYM?C2F|HAxPhH(!a#U#`a*-5Abb|4~@:RhJ9:4E|R@52B5G|F:2<BaY?C2F|Sc^#Xa2j=Qq5CJvB!-g<v{z;hhM?C2F|Zi[vrv{z;hiM?C2F|XKsA>!a)-g<v{z;h[eb?C2F|]i[vrv{z;h]eb?C2F|^iZu.vix,hZq3ah!.(?C2F|QOu!5ShXM:2<BaY?C2F|P", 13494, 2713, 49, 25, 61);

  // ../unison-sources/node_modules/entities/dist/generated/decode-data-xml.js
  var xmlDecodeTree = /* @__PURE__ */ new Uint16Array([
    512,
    26465,
    29036,
    7,
    0,
    2,
    4,
    116,
    24638,
    116,
    24636,
    8693,
    29807,
    24610,
    621,
    1,
    0,
    0,
    3,
    112,
    24614,
    111,
    115,
    24615
  ]);

  // ../unison-sources/node_modules/entities/dist/internal/bin-trie-flags.js
  var BinTrieFlags;
  (function(BinTrieFlags2) {
    BinTrieFlags2[BinTrieFlags2["VALUE_LENGTH"] = 49152] = "VALUE_LENGTH";
    BinTrieFlags2[BinTrieFlags2["FLAG13"] = 8192] = "FLAG13";
    BinTrieFlags2[BinTrieFlags2["BRANCH_LENGTH"] = 8064] = "BRANCH_LENGTH";
    BinTrieFlags2[BinTrieFlags2["JUMP_TABLE"] = 127] = "JUMP_TABLE";
    BinTrieFlags2[BinTrieFlags2["VALUE_MASK"] = 8191] = "VALUE_MASK";
  })(BinTrieFlags || (BinTrieFlags = {}));

  // ../unison-sources/node_modules/entities/dist/decode.js
  var CharCodes;
  (function(CharCodes3) {
    CharCodes3[CharCodes3["AMP"] = 38] = "AMP";
    CharCodes3[CharCodes3["NUM"] = 35] = "NUM";
    CharCodes3[CharCodes3["SEMI"] = 59] = "SEMI";
    CharCodes3[CharCodes3["EQUALS"] = 61] = "EQUALS";
    CharCodes3[CharCodes3["ZERO"] = 48] = "ZERO";
    CharCodes3[CharCodes3["NINE"] = 57] = "NINE";
    CharCodes3[CharCodes3["LOWER_A"] = 97] = "LOWER_A";
    CharCodes3[CharCodes3["LOWER_X"] = 120] = "LOWER_X";
  })(CharCodes || (CharCodes = {}));
  var TO_LOWER_BIT = 32;
  function isNumber(code) {
    return code - CharCodes.ZERO >>> 0 <= 9;
  }
  function isHexadecimalCharacter(code) {
    return (code | TO_LOWER_BIT) - CharCodes.LOWER_A >>> 0 <= 5;
  }
  function isAlpha(code) {
    return (code | TO_LOWER_BIT) - CharCodes.LOWER_A >>> 0 <= 25;
  }
  function isEntityInAttributeInvalidEnd(code) {
    return code === CharCodes.EQUALS || isAlpha(code) || isNumber(code);
  }
  var EntityDecoderState;
  (function(EntityDecoderState2) {
    EntityDecoderState2[EntityDecoderState2["EntityStart"] = 0] = "EntityStart";
    EntityDecoderState2[EntityDecoderState2["NumericStart"] = 1] = "NumericStart";
    EntityDecoderState2[EntityDecoderState2["NumericDecimal"] = 2] = "NumericDecimal";
    EntityDecoderState2[EntityDecoderState2["NumericHex"] = 3] = "NumericHex";
    EntityDecoderState2[EntityDecoderState2["NamedEntity"] = 4] = "NamedEntity";
  })(EntityDecoderState || (EntityDecoderState = {}));
  var DecodingMode;
  (function(DecodingMode2) {
    DecodingMode2[DecodingMode2["Legacy"] = 0] = "Legacy";
    DecodingMode2[DecodingMode2["Strict"] = 1] = "Strict";
    DecodingMode2[DecodingMode2["Attribute"] = 2] = "Attribute";
  })(DecodingMode || (DecodingMode = {}));
  var EntityDecoder = class {
    constructor(decodeTree, emitCodePoint, errors) {
      __publicField(this, "decodeTree");
      __publicField(this, "emitCodePoint");
      __publicField(this, "errors");
      /** The current state of the decoder. */
      __publicField(this, "state", EntityDecoderState.EntityStart);
      /** Characters that were consumed while parsing an entity. */
      __publicField(this, "consumed", 1);
      /**
       * The result of the entity.
       *
       * For named entities: the trie index of the best legacy match so far
       * (0 = none). For numeric entities: the accumulated code point.
       */
      __publicField(this, "result", 0);
      /** The current index in the decode tree. */
      __publicField(this, "treeIndex", 0);
      /**
       * Characters consumed since the last recorded legacy match, plus one.
       * Invariant at the top of the `stateNamedEntity` loop: `excess` equals
       * the number of unrecorded consumed characters + 1.
       */
      // biome-ignore lint/correctness/noUnusedPrivateClassMembers: False positive (read via destructuring)
      __publicField(this, "excess", 1);
      /** The mode in which the decoder is operating. */
      __publicField(this, "decodeMode", DecodingMode.Strict);
      /** The number of characters that have been consumed in the current run. */
      // biome-ignore lint/correctness/noUnusedPrivateClassMembers: False positive
      __publicField(this, "runConsumed", 0);
      this.decodeTree = decodeTree;
      this.emitCodePoint = emitCodePoint;
      this.errors = errors;
    }
    /**
     * Resets the instance to make it reusable.
     * @param decodeMode Entity decoding mode to use.
     */
    startEntity(decodeMode) {
      this.decodeMode = decodeMode;
      this.state = EntityDecoderState.EntityStart;
      this.result = 0;
      this.treeIndex = 0;
      this.excess = 1;
      this.consumed = 1;
      this.runConsumed = 0;
    }
    /**
     * Write an entity to the decoder. This can be called multiple times with partial entities.
     * If the entity is incomplete, the decoder will return -1.
     *
     * Mirrors the non-streaming `decodeWithTrie`, but with the ability to stop decoding if the
     * entity is incomplete, and resume when the next string is written.
     * @param input The string containing the entity (or a continuation of the entity).
     * @param offset The offset at which the entity begins. Should be 0 if this is not the first call.
     * @returns The number of characters that were consumed, or -1 if the entity is incomplete.
     */
    write(input, offset) {
      switch (this.state) {
        case EntityDecoderState.EntityStart: {
          if (input.charCodeAt(offset) === CharCodes.NUM) {
            this.state = EntityDecoderState.NumericStart;
            this.consumed += 1;
            return this.stateNumericStart(input, offset + 1);
          }
          this.state = EntityDecoderState.NamedEntity;
          return this.stateNamedEntity(input, offset);
        }
        case EntityDecoderState.NumericStart: {
          return this.stateNumericStart(input, offset);
        }
        case EntityDecoderState.NumericDecimal: {
          return this.stateNumericDecimal(input, offset);
        }
        case EntityDecoderState.NumericHex: {
          return this.stateNumericHex(input, offset);
        }
        default: {
          return this.stateNamedEntity(input, offset);
        }
      }
    }
    /**
     * Switches between the numeric decimal and hexadecimal states.
     *
     * Equivalent to the `Numeric character reference state` in the HTML spec.
     * @param input The string containing the entity (or a continuation of the entity).
     * @param offset The current offset.
     * @returns The number of characters that were consumed, or -1 if the entity is incomplete.
     */
    // eslint-disable-next-line unicorn/consistent-class-member-order
    stateNumericStart(input, offset) {
      if (offset >= input.length) {
        return -1;
      }
      if ((input.charCodeAt(offset) | TO_LOWER_BIT) === CharCodes.LOWER_X) {
        this.state = EntityDecoderState.NumericHex;
        this.consumed += 1;
        return this.stateNumericHex(input, offset + 1);
      }
      this.state = EntityDecoderState.NumericDecimal;
      return this.stateNumericDecimal(input, offset);
    }
    /**
     * Parses a hexadecimal numeric entity.
     *
     * Equivalent to the `Hexademical character reference state` in the HTML
     * spec. Digit parsing matches the hex loop in `parseNumericEntity`.
     * The accumulated value is preserved for numeric validation callbacks.
     * @param input The string containing the entity (or a continuation of the entity).
     * @param offset The current offset.
     * @returns The number of characters that were consumed, or -1 if the entity is incomplete.
     */
    stateNumericHex(input, offset) {
      const inputLength = input.length;
      let { result } = this;
      let { consumed } = this;
      while (offset < inputLength) {
        const char = input.charCodeAt(offset);
        if (isNumber(char) || isHexadecimalCharacter(char)) {
          const digit = char <= CharCodes.NINE ? char - CharCodes.ZERO : (char | TO_LOWER_BIT) - CharCodes.LOWER_A + 10;
          result = result * 16 + digit;
          consumed += 1;
          offset += 1;
        } else {
          this.result = result;
          this.consumed = consumed;
          return this.emitNumericEntity(char, 3);
        }
      }
      this.result = result;
      this.consumed = consumed;
      return -1;
    }
    /**
     * Parses a decimal numeric entity.
     *
     * Equivalent to the `Decimal character reference state` in the HTML
     * spec. Digit parsing matches the decimal loop in `parseNumericEntity`.
     * The accumulated value is preserved for numeric validation callbacks.
     * @param input The string containing the entity (or a continuation of the entity).
     * @param offset The current offset.
     * @returns The number of characters that were consumed, or -1 if the entity is incomplete.
     */
    stateNumericDecimal(input, offset) {
      const inputLength = input.length;
      let { result } = this;
      let { consumed } = this;
      while (offset < inputLength) {
        const digit = input.charCodeAt(offset) - CharCodes.ZERO;
        if (digit >>> 0 > 9) {
          this.result = result;
          this.consumed = consumed;
          return this.emitNumericEntity(digit + CharCodes.ZERO, 2);
        }
        result = result * 10 + digit;
        consumed += 1;
        offset += 1;
      }
      this.result = result;
      this.consumed = consumed;
      return -1;
    }
    /**
     * Validate and emit a numeric entity.
     *
     * Implements the logic from the `Hexademical character reference start
     * state` and `Numeric character reference end state` in the HTML spec.
     * @param lastCp The last code point of the entity. Used to see if the
     *               entity was terminated with a semicolon.
     * @param expectedLength The minimum number of characters that should be
     *                       consumed. Used to validate that at least one digit
     *                       was consumed.
     * @returns The number of characters that were consumed.
     */
    emitNumericEntity(lastCp, expectedLength) {
      if (this.consumed <= expectedLength) {
        this.errors?.absenceOfDigitsInNumericCharacterReference(this.consumed);
        return 0;
      }
      if (lastCp === CharCodes.SEMI) {
        this.consumed += 1;
      } else if (this.decodeMode === DecodingMode.Strict) {
        return 0;
      }
      this.emitCodePoint((this.decodeTree === xmlDecodeTree ? replaceCodePointXML : replaceCodePoint)(this.result), this.consumed);
      if (this.errors) {
        if (lastCp !== CharCodes.SEMI) {
          this.errors.missingSemicolonAfterCharacterReference();
        }
        this.errors.validateNumericCharacterReference(this.result);
      }
      return this.consumed;
    }
    /**
     * Flush locally-tracked walk state back to the fields, then emit the
     * recorded legacy match or reject (cold path — at most once per
     * entity). Called after failed navigation (leaf node, branch miss, or
     * compact-run mismatch). In attribute mode, reject if no legacy was
     * recorded at the current node, if we descended past it, or if the
     * pending input character is an invalid attribute terminator.
     * @param consumed Locally-tracked consumed count.
     * @param excess Locally-tracked excess count.
     * @param char Pending input character (may be the mismatching char).
     * @param valueLength Value length at the current trie node.
     */
    flushAndEmitLegacyOrReject(consumed, excess, char, valueLength) {
      this.consumed = consumed;
      this.excess = excess;
      return this.result === 0 || this.decodeMode === DecodingMode.Attribute && (valueLength === 0 || excess > 1 || isEntityInAttributeInvalidEnd(char)) ? 0 : this.emitNotTerminatedNamedEntity();
    }
    /**
     * Parses a named entity.
     *
     * Equivalent to the `Named character reference state` in the HTML spec.
     * @param input The string containing the entity (or a continuation of the entity).
     * @param offset The current offset.
     * @returns The number of characters that were consumed, or -1 if the entity is incomplete.
     */
    stateNamedEntity(input, offset) {
      const { decodeTree } = this;
      const inputLength = input.length;
      const isStrict = this.decodeMode === DecodingMode.Strict;
      let { treeIndex } = this;
      let { excess } = this;
      let { consumed } = this;
      let current = decodeTree[treeIndex];
      while (offset < inputLength) {
        while ((current & (BinTrieFlags.VALUE_LENGTH | BinTrieFlags.FLAG13)) === 0 && (current & BinTrieFlags.JUMP_TABLE) !== 0) {
          const char2 = input.charCodeAt(offset);
          const jumpOffset = current & BinTrieFlags.JUMP_TABLE;
          const branchCount = (current & BinTrieFlags.BRANCH_LENGTH) >> 7;
          if (branchCount === 0) {
            if (char2 !== jumpOffset) {
              return this.flushAndEmitLegacyOrReject(consumed, excess, char2, 0);
            }
            treeIndex += 1;
          } else {
            const slot = char2 - jumpOffset;
            if (slot >>> 0 >= branchCount) {
              return this.flushAndEmitLegacyOrReject(consumed, excess, char2, 0);
            }
            const stored = decodeTree[treeIndex + 1 + slot];
            if (stored === 0) {
              return this.flushAndEmitLegacyOrReject(consumed, excess, char2, 0);
            }
            treeIndex = treeIndex + branchCount + stored & 65535;
          }
          current = decodeTree[treeIndex];
          offset += 1;
          excess += 1;
          if (offset >= inputLength)
            break;
        }
        if (offset >= inputLength)
          break;
        if ((current & (BinTrieFlags.VALUE_LENGTH | BinTrieFlags.FLAG13)) === BinTrieFlags.FLAG13) {
          const runLength = (current & BinTrieFlags.BRANCH_LENGTH) >> 7;
          let { runConsumed } = this;
          if (runConsumed === 0) {
            const char2 = input.charCodeAt(offset);
            if (char2 !== (current & BinTrieFlags.JUMP_TABLE)) {
              return this.flushAndEmitLegacyOrReject(consumed, excess, char2, 0);
            }
            offset += 1;
            excess += 1;
            runConsumed = 1;
          }
          while (runConsumed < runLength) {
            if (offset >= inputLength) {
              this.treeIndex = treeIndex;
              this.excess = excess;
              this.consumed = consumed;
              this.runConsumed = runConsumed;
              return -1;
            }
            const charIndexInPacked = runConsumed - 1;
            const packedWord = decodeTree[treeIndex + 1 + (charIndexInPacked >> 1)];
            const expectedChar = packedWord >> ((charIndexInPacked & 1) << 3) & 255;
            const char2 = input.charCodeAt(offset);
            if (char2 !== expectedChar) {
              this.runConsumed = 0;
              return this.flushAndEmitLegacyOrReject(consumed, excess, char2, 0);
            }
            offset += 1;
            excess += 1;
            runConsumed += 1;
          }
          this.runConsumed = 0;
          treeIndex += 1 + (runLength >> 1);
          current = decodeTree[treeIndex];
          continue;
        }
        const valueLength = current >>> 14;
        const char = input.charCodeAt(offset);
        if (valueLength !== 0) {
          if (!isStrict && (current & BinTrieFlags.FLAG13) === 0) {
            this.result = treeIndex;
            consumed += excess - 1;
            excess = 1;
          }
          if (char === CharCodes.SEMI) {
            return this.emitNamedEntityData(treeIndex, valueLength, consumed + excess);
          }
          if (valueLength === 1) {
            return this.flushAndEmitLegacyOrReject(consumed, excess, char, valueLength);
          }
        }
        const next = determineBranch(decodeTree, current, treeIndex + (valueLength || 1), char);
        if (next < 0) {
          return this.flushAndEmitLegacyOrReject(consumed, excess, char, valueLength);
        }
        treeIndex = next;
        current = decodeTree[treeIndex];
        offset += 1;
        excess += 1;
      }
      if (!isStrict && current >>> 14 !== 0 && (current & BinTrieFlags.FLAG13) === 0) {
        this.result = treeIndex;
        consumed += excess - 1;
        excess = 1;
      }
      this.treeIndex = treeIndex;
      this.excess = excess;
      this.consumed = consumed;
      return -1;
    }
    /**
     * Emit a named entity that was not terminated with a semicolon.
     * @returns The number of characters consumed.
     */
    emitNotTerminatedNamedEntity() {
      const { result, decodeTree } = this;
      const valueLength = decodeTree[result] >>> 14;
      this.emitNamedEntityData(result, valueLength, this.consumed);
      this.errors?.missingSemicolonAfterCharacterReference();
      return this.consumed;
    }
    /**
     * Emit a named entity.
     * @param result The index of the entity in the decode tree.
     * @param valueLength Encoded value length (header plus any value words).
     * @param consumed The number of characters consumed.
     * @returns The number of characters consumed.
     */
    emitNamedEntityData(result, valueLength, consumed) {
      const { decodeTree } = this;
      this.emitCodePoint(valueLength === 1 ? decodeTree[result] & BinTrieFlags.VALUE_MASK : decodeTree[result + 1], consumed);
      if (valueLength === 3) {
        this.emitCodePoint(decodeTree[result + 2], consumed);
      }
      return consumed;
    }
    /**
     * Signal to the parser that the end of the input was reached.
     *
     * Remaining data will be emitted and relevant errors will be produced.
     * @returns The number of characters consumed.
     */
    end() {
      switch (this.state) {
        case EntityDecoderState.NamedEntity: {
          return this.result !== 0 && (this.decodeMode !== DecodingMode.Attribute || this.result === this.treeIndex) ? this.emitNotTerminatedNamedEntity() : 0;
        }
        // Otherwise, emit a numeric entity if we have one.
        case EntityDecoderState.NumericDecimal: {
          return this.emitNumericEntity(0, 2);
        }
        case EntityDecoderState.NumericHex: {
          return this.emitNumericEntity(0, 3);
        }
        case EntityDecoderState.NumericStart: {
          this.errors?.absenceOfDigitsInNumericCharacterReference(this.consumed);
          return 0;
        }
        default: {
          return 0;
        }
      }
    }
  };
  function determineBranch(decodeTree, current, nodeIndex, char) {
    const branchCount = (current & BinTrieFlags.BRANCH_LENGTH) >> 7;
    const jumpOffset = current & BinTrieFlags.JUMP_TABLE;
    if (jumpOffset) {
      if (branchCount === 0) {
        return char === jumpOffset ? nodeIndex : -1;
      }
      const slot = char - jumpOffset;
      if (slot >>> 0 >= branchCount)
        return -1;
      const stored = decodeTree[nodeIndex + slot];
      return stored === 0 ? -1 : nodeIndex + branchCount + stored - 1 & 65535;
    }
    if (branchCount === 0)
      return -1;
    const packedKeySlots = branchCount + 1 >> 1;
    const branchEnd = nodeIndex + packedKeySlots + branchCount;
    for (let index = 0; index < branchCount; index++) {
      const packed = decodeTree[nodeIndex + (index >> 1)];
      const key = packed >> ((index & 1) << 3) & 255;
      if (key === char) {
        const pointerIndex = nodeIndex + packedKeySlots + index;
        return branchEnd + decodeTree[pointerIndex] & 65535;
      }
      if (key > char)
        return -1;
    }
    return -1;
  }

  // ../unison-sources/node_modules/htmlparser2/dist/Tokenizer.js
  var CharCodes2;
  (function(CharCodes3) {
    CharCodes3[CharCodes3["Tab"] = 9] = "Tab";
    CharCodes3[CharCodes3["NewLine"] = 10] = "NewLine";
    CharCodes3[CharCodes3["FormFeed"] = 12] = "FormFeed";
    CharCodes3[CharCodes3["CarriageReturn"] = 13] = "CarriageReturn";
    CharCodes3[CharCodes3["Space"] = 32] = "Space";
    CharCodes3[CharCodes3["ExclamationMark"] = 33] = "ExclamationMark";
    CharCodes3[CharCodes3["Number"] = 35] = "Number";
    CharCodes3[CharCodes3["Amp"] = 38] = "Amp";
    CharCodes3[CharCodes3["SingleQuote"] = 39] = "SingleQuote";
    CharCodes3[CharCodes3["DoubleQuote"] = 34] = "DoubleQuote";
    CharCodes3[CharCodes3["Dash"] = 45] = "Dash";
    CharCodes3[CharCodes3["Slash"] = 47] = "Slash";
    CharCodes3[CharCodes3["Zero"] = 48] = "Zero";
    CharCodes3[CharCodes3["Nine"] = 57] = "Nine";
    CharCodes3[CharCodes3["Semi"] = 59] = "Semi";
    CharCodes3[CharCodes3["Lt"] = 60] = "Lt";
    CharCodes3[CharCodes3["Eq"] = 61] = "Eq";
    CharCodes3[CharCodes3["Gt"] = 62] = "Gt";
    CharCodes3[CharCodes3["Questionmark"] = 63] = "Questionmark";
    CharCodes3[CharCodes3["UpperA"] = 65] = "UpperA";
    CharCodes3[CharCodes3["LowerA"] = 97] = "LowerA";
    CharCodes3[CharCodes3["UpperF"] = 70] = "UpperF";
    CharCodes3[CharCodes3["LowerF"] = 102] = "LowerF";
    CharCodes3[CharCodes3["UpperZ"] = 90] = "UpperZ";
    CharCodes3[CharCodes3["LowerZ"] = 122] = "LowerZ";
    CharCodes3[CharCodes3["LowerX"] = 120] = "LowerX";
    CharCodes3[CharCodes3["OpeningSquareBracket"] = 91] = "OpeningSquareBracket";
  })(CharCodes2 || (CharCodes2 = {}));
  var State;
  (function(State2) {
    State2[State2["Text"] = 1] = "Text";
    State2[State2["BeforeTagName"] = 2] = "BeforeTagName";
    State2[State2["InTagName"] = 3] = "InTagName";
    State2[State2["InSelfClosingTag"] = 4] = "InSelfClosingTag";
    State2[State2["BeforeClosingTagName"] = 5] = "BeforeClosingTagName";
    State2[State2["InClosingTagName"] = 6] = "InClosingTagName";
    State2[State2["AfterClosingTagName"] = 7] = "AfterClosingTagName";
    State2[State2["BeforeAttributeName"] = 8] = "BeforeAttributeName";
    State2[State2["InAttributeName"] = 9] = "InAttributeName";
    State2[State2["AfterAttributeName"] = 10] = "AfterAttributeName";
    State2[State2["BeforeAttributeValue"] = 11] = "BeforeAttributeValue";
    State2[State2["InAttributeValueDq"] = 12] = "InAttributeValueDq";
    State2[State2["InAttributeValueSq"] = 13] = "InAttributeValueSq";
    State2[State2["InAttributeValueNq"] = 14] = "InAttributeValueNq";
    State2[State2["BeforeDeclaration"] = 15] = "BeforeDeclaration";
    State2[State2["InDeclaration"] = 16] = "InDeclaration";
    State2[State2["InProcessingInstruction"] = 17] = "InProcessingInstruction";
    State2[State2["BeforeComment"] = 18] = "BeforeComment";
    State2[State2["CDATASequence"] = 19] = "CDATASequence";
    State2[State2["DeclarationSequence"] = 20] = "DeclarationSequence";
    State2[State2["InSpecialComment"] = 21] = "InSpecialComment";
    State2[State2["InCommentLike"] = 22] = "InCommentLike";
    State2[State2["SpecialStartSequence"] = 23] = "SpecialStartSequence";
    State2[State2["InSpecialTag"] = 24] = "InSpecialTag";
    State2[State2["InPlainText"] = 25] = "InPlainText";
    State2[State2["InEntity"] = 26] = "InEntity";
  })(State || (State = {}));
  function isWhitespace(c) {
    return c === CharCodes2.Space || c === CharCodes2.NewLine || c === CharCodes2.Tab || c === CharCodes2.FormFeed || c === CharCodes2.CarriageReturn;
  }
  function isEndOfTagSection(c) {
    return c === CharCodes2.Slash || c === CharCodes2.Gt || isWhitespace(c);
  }
  function isASCIIAlpha(c) {
    return c >= CharCodes2.LowerA && c <= CharCodes2.LowerZ || c >= CharCodes2.UpperA && c <= CharCodes2.UpperZ;
  }
  var QuoteType;
  (function(QuoteType2) {
    QuoteType2[QuoteType2["NoValue"] = 0] = "NoValue";
    QuoteType2[QuoteType2["Unquoted"] = 1] = "Unquoted";
    QuoteType2[QuoteType2["Single"] = 2] = "Single";
    QuoteType2[QuoteType2["Double"] = 3] = "Double";
  })(QuoteType || (QuoteType = {}));
  var Sequences = {
    Empty: new Uint8Array(0),
    Cdata: new Uint8Array([67, 68, 65, 84, 65, 91]),
    // CDATA[
    CdataEnd: new Uint8Array([93, 93, 62]),
    // ]]>
    CommentEnd: new Uint8Array([45, 45, 33, 62]),
    // `--!>`
    Doctype: new Uint8Array([100, 111, 99, 116, 121, 112, 101]),
    // `doctype`
    IframeEnd: new Uint8Array([60, 47, 105, 102, 114, 97, 109, 101]),
    // `</iframe`
    NoembedEnd: new Uint8Array([
      60,
      47,
      110,
      111,
      101,
      109,
      98,
      101,
      100
    ]),
    // `</noembed`
    NoframesEnd: new Uint8Array([
      60,
      47,
      110,
      111,
      102,
      114,
      97,
      109,
      101,
      115
    ]),
    // `</noframes`
    Plaintext: new Uint8Array([
      60,
      47,
      112,
      108,
      97,
      105,
      110,
      116,
      101,
      120,
      116
    ]),
    // `</plaintext`
    ScriptEnd: new Uint8Array([60, 47, 115, 99, 114, 105, 112, 116]),
    // `</script`
    StyleEnd: new Uint8Array([60, 47, 115, 116, 121, 108, 101]),
    // `</style`
    TitleEnd: new Uint8Array([60, 47, 116, 105, 116, 108, 101]),
    // `</title`
    TextareaEnd: new Uint8Array([
      60,
      47,
      116,
      101,
      120,
      116,
      97,
      114,
      101,
      97
    ]),
    // `</textarea`
    XmpEnd: new Uint8Array([60, 47, 120, 109, 112])
    // `</xmp`
  };
  var specialStartSequences = /* @__PURE__ */ new Map([
    [Sequences.IframeEnd[2], Sequences.IframeEnd],
    [Sequences.NoembedEnd[2], Sequences.NoembedEnd],
    [Sequences.Plaintext[2], Sequences.Plaintext],
    [Sequences.ScriptEnd[2], Sequences.ScriptEnd],
    [Sequences.TitleEnd[2], Sequences.TitleEnd],
    [Sequences.XmpEnd[2], Sequences.XmpEnd]
  ]);
  var Tokenizer = class {
    constructor({ xmlMode = false, decodeEntities = true, recognizeSelfClosing = xmlMode }, cbs) {
      __publicField(this, "cbs");
      /** The current state the tokenizer is in. */
      __publicField(this, "state", State.Text);
      /** The read buffer. */
      __publicField(this, "buffer", "");
      /** The beginning of the section that is currently being read. */
      __publicField(this, "sectionStart", 0);
      /** The index within the buffer that we are currently looking at. */
      __publicField(this, "index", 0);
      /** The start of the last entity. */
      __publicField(this, "entityStart", 0);
      /** Some behavior, eg. when decoding entities, is done while we are in another state. This keeps track of the other state type. */
      __publicField(this, "baseState", State.Text);
      /** For special parsing behavior inside of script and style tags. */
      __publicField(this, "isSpecial", false);
      /** Indicates whether the tokenizer has been paused. */
      __publicField(this, "running", true);
      /** The offset of the current buffer. */
      __publicField(this, "offset", 0);
      __publicField(this, "xmlMode");
      __publicField(this, "decodeEntities");
      __publicField(this, "recognizeSelfClosing");
      __publicField(this, "entityDecoder");
      __publicField(this, "currentSequence", Sequences.Empty);
      __publicField(this, "sequenceIndex", 0);
      this.cbs = cbs;
      this.xmlMode = xmlMode;
      this.decodeEntities = decodeEntities;
      this.recognizeSelfClosing = recognizeSelfClosing;
      this.entityDecoder = new EntityDecoder(xmlMode ? xmlDecodeTree : htmlDecodeTree, (cp, consumed) => this.emitCodePoint(cp, consumed));
    }
    reset() {
      this.state = State.Text;
      this.buffer = "";
      this.sectionStart = 0;
      this.index = 0;
      this.baseState = State.Text;
      this.isSpecial = false;
      this.currentSequence = Sequences.Empty;
      this.sequenceIndex = 0;
      this.running = true;
      this.offset = 0;
    }
    write(chunk) {
      this.offset += this.buffer.length;
      this.buffer = chunk;
      this.parse();
    }
    end() {
      if (this.running)
        this.finish();
    }
    pause() {
      this.running = false;
    }
    resume() {
      this.running = true;
      if (this.index < this.buffer.length + this.offset) {
        this.parse();
      }
    }
    stateText(c) {
      if (c === CharCodes2.Lt || !this.decodeEntities && this.fastForwardTo(CharCodes2.Lt)) {
        if (this.index > this.sectionStart) {
          this.cbs.ontext(this.sectionStart, this.index);
        }
        this.state = State.BeforeTagName;
        this.sectionStart = this.index;
      } else if (this.decodeEntities && c === CharCodes2.Amp) {
        this.startEntity();
      }
    }
    enterTagBody() {
      if (this.currentSequence === Sequences.Plaintext) {
        this.currentSequence = Sequences.Empty;
        this.state = State.InPlainText;
      } else if (this.isSpecial) {
        this.state = State.InSpecialTag;
        this.sequenceIndex = 0;
      } else {
        this.state = State.Text;
      }
    }
    /**
     * Match the opening tag name against an HTML text-only tag sequence.
     *
     * Some tags share an initial prefix (`script`/`style`, `title`/`textarea`,
     * `noembed`/`noframes`), so we may switch to an alternate sequence at the
     * first distinguishing byte.  On a successful full match we fall back to
     * the normal tag-name state; a later `>` will enter raw-text, RCDATA, or
     * plaintext mode based on `currentSequence` / `isSpecial`.
     * @param c Current character code point.
     */
    stateSpecialStartSequence(c) {
      const lower = c | 32;
      if (this.sequenceIndex < this.currentSequence.length) {
        if (lower === this.currentSequence[this.sequenceIndex]) {
          this.sequenceIndex++;
          return;
        }
        if (this.sequenceIndex === 3) {
          if (this.currentSequence === Sequences.ScriptEnd && lower === Sequences.StyleEnd[3]) {
            this.currentSequence = Sequences.StyleEnd;
            this.sequenceIndex = 4;
            return;
          }
          if (this.currentSequence === Sequences.TitleEnd && lower === Sequences.TextareaEnd[3]) {
            this.currentSequence = Sequences.TextareaEnd;
            this.sequenceIndex = 4;
            return;
          }
        } else if (this.sequenceIndex === 4 && this.currentSequence === Sequences.NoembedEnd && lower === Sequences.NoframesEnd[4]) {
          this.currentSequence = Sequences.NoframesEnd;
          this.sequenceIndex = 5;
          return;
        }
      } else if (isEndOfTagSection(c)) {
        this.sequenceIndex = 0;
        this.state = State.InTagName;
        this.stateInTagName(c);
        return;
      }
      this.isSpecial = false;
      this.currentSequence = Sequences.Empty;
      this.sequenceIndex = 0;
      this.state = State.InTagName;
      this.stateInTagName(c);
    }
    stateCDATASequence(c) {
      if (c === Sequences.Cdata[this.sequenceIndex]) {
        if (++this.sequenceIndex === Sequences.Cdata.length) {
          this.state = State.InCommentLike;
          this.currentSequence = Sequences.CdataEnd;
          this.sequenceIndex = 0;
          this.sectionStart = this.index + 1;
        }
      } else {
        this.sequenceIndex = 0;
        if (this.xmlMode) {
          this.state = State.InDeclaration;
          this.stateInDeclaration(c);
        } else {
          this.state = State.InSpecialComment;
          this.stateInSpecialComment(c);
        }
      }
    }
    /**
     * When we wait for one specific character, we can speed things up
     * by skipping through the buffer until we find it.
     * @param c Current character code point.
     * @returns Whether the character was found.
     */
    fastForwardTo(c) {
      while (++this.index < this.buffer.length + this.offset) {
        if (this.buffer.charCodeAt(this.index - this.offset) === c) {
          return true;
        }
      }
      this.index = this.buffer.length + this.offset - 1;
      return false;
    }
    /**
     * Emit a comment token and return to the text state.
     * @param offset Number of characters in the end sequence that have already been matched.
     */
    emitComment(offset) {
      this.cbs.oncomment(this.sectionStart, this.index, offset);
      this.sequenceIndex = 0;
      this.sectionStart = this.index + 1;
      this.state = State.Text;
    }
    /**
     * Comments and CDATA end with `-->` and `]]>`.
     *
     * Their common qualities are:
     * - Their end sequences have a distinct character they start with.
     * - That character is then repeated, so we have to check multiple repeats.
     * - All characters but the start character of the sequence can be skipped.
     * @param c Current character code point.
     */
    stateInCommentLike(c) {
      if (!this.xmlMode && this.currentSequence === Sequences.CommentEnd && this.sequenceIndex <= 1 && /*
       * We're still at the very start of the comment: the only
       * characters consumed since `<!--` are the dashes that
       * advanced sequenceIndex (0 for `<!-->`, 1 for `<!--->`).
       */
      this.index === this.sectionStart + this.sequenceIndex && c === CharCodes2.Gt) {
        this.emitComment(this.sequenceIndex);
      } else if (this.currentSequence === Sequences.CommentEnd && this.sequenceIndex === 2 && c === CharCodes2.Gt) {
        this.emitComment(2);
      } else if (this.currentSequence === Sequences.CommentEnd && this.sequenceIndex === this.currentSequence.length - 1 && c !== CharCodes2.Gt) {
        this.sequenceIndex = Number(c === CharCodes2.Dash);
      } else if (c === this.currentSequence[this.sequenceIndex]) {
        if (++this.sequenceIndex === this.currentSequence.length) {
          if (this.currentSequence === Sequences.CdataEnd) {
            this.cbs.oncdata(this.sectionStart, this.index, 2);
          } else {
            this.cbs.oncomment(this.sectionStart, this.index, 3);
          }
          this.sequenceIndex = 0;
          this.sectionStart = this.index + 1;
          this.state = State.Text;
        }
      } else if (this.sequenceIndex === 0) {
        if (this.fastForwardTo(this.currentSequence[0])) {
          this.sequenceIndex = 1;
        }
      } else if (c !== this.currentSequence[this.sequenceIndex - 1]) {
        this.sequenceIndex = 0;
      }
    }
    /**
     * HTML only allows ASCII alpha characters (a-z and A-Z) at the beginning of a tag name.
     *
     * XML allows a lot more characters here (@see https://www.w3.org/TR/REC-xml/#NT-NameStartChar).
     * We allow anything that wouldn't end the tag.
     * @param c Current character code point.
     */
    isTagStartChar(c) {
      return this.xmlMode ? !isEndOfTagSection(c) : isASCIIAlpha(c);
    }
    /**
     * Scan raw-text / RCDATA content for the matching end tag.
     *
     * For RCDATA tags (`<title>`, `<textarea>`) entities are decoded inline.
     * For raw-text tags (`<script>`, `<style>`, etc.) we fast-forward to `<`.
     * @param c Current character code point.
     */
    stateInSpecialTag(c) {
      if (this.sequenceIndex === this.currentSequence.length) {
        if (isEndOfTagSection(c)) {
          const endOfText = this.index - this.currentSequence.length;
          if (this.sectionStart < endOfText) {
            const actualIndex = this.index;
            this.index = endOfText;
            this.cbs.ontext(this.sectionStart, endOfText);
            this.index = actualIndex;
          }
          this.isSpecial = false;
          this.sectionStart = endOfText + 2;
          this.stateInClosingTagName(c);
          return;
        }
        this.sequenceIndex = 0;
      }
      if ((c | 32) === this.currentSequence[this.sequenceIndex]) {
        this.sequenceIndex += 1;
      } else if (this.sequenceIndex === 0) {
        if (this.currentSequence === Sequences.TitleEnd || this.currentSequence === Sequences.TextareaEnd) {
          if (this.decodeEntities && c === CharCodes2.Amp) {
            this.startEntity();
          }
        } else if (this.fastForwardTo(CharCodes2.Lt)) {
          this.sequenceIndex = 1;
        }
      } else {
        this.sequenceIndex = Number(c === CharCodes2.Lt);
      }
    }
    stateBeforeTagName(c) {
      if (c === CharCodes2.ExclamationMark) {
        this.state = State.BeforeDeclaration;
        this.sectionStart = this.index + 1;
      } else if (c === CharCodes2.Questionmark) {
        if (this.xmlMode) {
          this.state = State.InProcessingInstruction;
          this.sequenceIndex = 0;
          this.sectionStart = this.index + 1;
        } else {
          this.state = State.InSpecialComment;
          this.sectionStart = this.index;
        }
      } else if (this.isTagStartChar(c)) {
        this.sectionStart = this.index;
        const special = this.xmlMode || this.cbs.isInForeignContext?.() ? void 0 : specialStartSequences.get(c | 32);
        if (special === void 0) {
          this.state = State.InTagName;
        } else {
          this.isSpecial = true;
          this.currentSequence = special;
          this.sequenceIndex = 3;
          this.state = State.SpecialStartSequence;
        }
      } else if (c === CharCodes2.Slash) {
        this.state = State.BeforeClosingTagName;
      } else {
        this.state = State.Text;
        this.stateText(c);
      }
    }
    stateInTagName(c) {
      if (isEndOfTagSection(c)) {
        this.cbs.onopentagname(this.sectionStart, this.index);
        this.sectionStart = -1;
        this.state = State.BeforeAttributeName;
        this.stateBeforeAttributeName(c);
      }
    }
    stateBeforeClosingTagName(c) {
      if (isWhitespace(c)) {
        if (this.xmlMode) {
        } else {
          this.state = State.InSpecialComment;
          this.sectionStart = this.index;
        }
      } else if (c === CharCodes2.Gt) {
        this.state = State.Text;
        if (!this.xmlMode) {
          this.sectionStart = this.index + 1;
        }
      } else {
        this.state = this.isTagStartChar(c) ? State.InClosingTagName : State.InSpecialComment;
        this.sectionStart = this.index;
      }
    }
    stateInClosingTagName(c) {
      if (isEndOfTagSection(c)) {
        this.cbs.onclosetag(this.sectionStart, this.index);
        this.sectionStart = -1;
        this.state = State.AfterClosingTagName;
        this.stateAfterClosingTagName(c);
      }
    }
    stateAfterClosingTagName(c) {
      if (c === CharCodes2.Gt || this.fastForwardTo(CharCodes2.Gt)) {
        this.state = State.Text;
        this.sectionStart = this.index + 1;
      }
    }
    stateBeforeAttributeName(c) {
      if (c === CharCodes2.Gt) {
        this.cbs.onopentagend(this.index);
        this.enterTagBody();
        this.sectionStart = this.index + 1;
      } else if (c === CharCodes2.Slash) {
        this.state = State.InSelfClosingTag;
      } else if (!isWhitespace(c)) {
        this.state = State.InAttributeName;
        this.sectionStart = this.index;
      }
    }
    /**
     * Handle `/` before `>` in an opening tag.
     *
     * In HTML mode, text-only tags ignore the self-closing flag and still enter
     * their raw-text/RCDATA/plaintext state unless self-closing tags are being
     * recognized. In XML mode, or for ordinary tags, the tokenizer returns to
     * regular text parsing after emitting the self-closing callback.
     * @param c Current character code point.
     */
    stateInSelfClosingTag(c) {
      if (c === CharCodes2.Gt) {
        this.cbs.onselfclosingtag(this.index);
        this.sectionStart = this.index + 1;
        if (!this.recognizeSelfClosing) {
          this.enterTagBody();
          return;
        }
        this.state = State.Text;
        this.isSpecial = false;
        this.currentSequence = Sequences.Empty;
      } else if (!isWhitespace(c)) {
        this.state = State.BeforeAttributeName;
        this.stateBeforeAttributeName(c);
      }
    }
    stateInAttributeName(c) {
      if (c === CharCodes2.Eq || isEndOfTagSection(c)) {
        this.cbs.onattribname(this.sectionStart, this.index);
        this.sectionStart = this.index;
        this.state = State.AfterAttributeName;
        this.stateAfterAttributeName(c);
      }
    }
    stateAfterAttributeName(c) {
      if (c === CharCodes2.Eq) {
        this.state = State.BeforeAttributeValue;
      } else if (c === CharCodes2.Slash || c === CharCodes2.Gt) {
        this.cbs.onattribend(QuoteType.NoValue, this.sectionStart);
        this.sectionStart = -1;
        this.state = State.BeforeAttributeName;
        this.stateBeforeAttributeName(c);
      } else if (!isWhitespace(c)) {
        this.cbs.onattribend(QuoteType.NoValue, this.sectionStart);
        this.state = State.InAttributeName;
        this.sectionStart = this.index;
      }
    }
    stateBeforeAttributeValue(c) {
      if (c === CharCodes2.DoubleQuote) {
        this.state = State.InAttributeValueDq;
        this.sectionStart = this.index + 1;
      } else if (c === CharCodes2.SingleQuote) {
        this.state = State.InAttributeValueSq;
        this.sectionStart = this.index + 1;
      } else if (!isWhitespace(c)) {
        this.sectionStart = this.index;
        this.state = State.InAttributeValueNq;
        this.stateInAttributeValueNoQuotes(c);
      }
    }
    handleInAttributeValue(c, quote) {
      if (c === quote || !this.decodeEntities && this.fastForwardTo(quote)) {
        this.cbs.onattribdata(this.sectionStart, this.index);
        this.sectionStart = -1;
        this.cbs.onattribend(quote === CharCodes2.DoubleQuote ? QuoteType.Double : QuoteType.Single, this.index + 1);
        this.state = State.BeforeAttributeName;
      } else if (this.decodeEntities && c === CharCodes2.Amp) {
        this.startEntity();
      }
    }
    stateInAttributeValueDoubleQuotes(c) {
      this.handleInAttributeValue(c, CharCodes2.DoubleQuote);
    }
    stateInAttributeValueSingleQuotes(c) {
      this.handleInAttributeValue(c, CharCodes2.SingleQuote);
    }
    stateInAttributeValueNoQuotes(c) {
      if (isWhitespace(c) || c === CharCodes2.Gt) {
        this.cbs.onattribdata(this.sectionStart, this.index);
        this.sectionStart = -1;
        this.cbs.onattribend(QuoteType.Unquoted, this.index);
        this.state = State.BeforeAttributeName;
        this.stateBeforeAttributeName(c);
      } else if (this.decodeEntities && c === CharCodes2.Amp) {
        this.startEntity();
      }
    }
    /**
     * Distinguish between CDATA, declarations, HTML comments, and HTML bogus
     * comments after `<!`.
     *
     * In HTML mode, only real comments and doctypes stay on declaration paths;
     * everything else becomes a bogus comment terminated by the next `>`.
     * @param c Current character code point.
     */
    stateBeforeDeclaration(c) {
      if (c === CharCodes2.OpeningSquareBracket) {
        this.state = State.CDATASequence;
        this.sequenceIndex = 0;
      } else if (this.xmlMode) {
        this.state = c === CharCodes2.Dash ? State.BeforeComment : State.InDeclaration;
      } else if ((c | 32) === Sequences.Doctype[0]) {
        this.state = State.DeclarationSequence;
        this.currentSequence = Sequences.Doctype;
        this.sequenceIndex = 1;
      } else if (c === CharCodes2.Gt) {
        this.cbs.oncomment(this.sectionStart, this.index, 0);
        this.state = State.Text;
        this.sectionStart = this.index + 1;
      } else if (c === CharCodes2.Dash) {
        this.state = State.BeforeComment;
      } else {
        this.state = State.InSpecialComment;
      }
    }
    /**
     * Continue matching `doctype` after `<!d`.
     *
     * A full `doctype` match stays on the declaration path; any other name falls
     * back to an HTML bogus comment, which matches browser behavior for
     * non-doctype `<!...>` constructs.
     * @param c Current character code point.
     */
    stateDeclarationSequence(c) {
      if (this.sequenceIndex === this.currentSequence.length) {
        this.state = State.InDeclaration;
        this.stateInDeclaration(c);
      } else if ((c | 32) === this.currentSequence[this.sequenceIndex]) {
        this.sequenceIndex += 1;
      } else if (c === CharCodes2.Gt) {
        this.cbs.oncomment(this.sectionStart, this.index, 0);
        this.state = State.Text;
        this.sectionStart = this.index + 1;
      } else {
        this.state = State.InSpecialComment;
      }
    }
    stateInDeclaration(c) {
      if (c === CharCodes2.Gt || this.fastForwardTo(CharCodes2.Gt)) {
        this.cbs.ondeclaration(this.sectionStart, this.index);
        this.state = State.Text;
        this.sectionStart = this.index + 1;
      }
    }
    /**
     * XML processing instructions (`<?...?>`).
     *
     * In HTML mode `<?` is routed to `InSpecialComment` instead, so this
     * state is only reachable in XML mode.
     * @param c Current character code point.
     */
    stateInProcessingInstruction(c) {
      if (c === CharCodes2.Questionmark) {
        this.sequenceIndex = 1;
      } else if (c === CharCodes2.Gt && this.sequenceIndex === 1) {
        this.cbs.onprocessinginstruction(this.sectionStart, this.index - 1);
        this.sequenceIndex = 0;
        this.state = State.Text;
        this.sectionStart = this.index + 1;
      } else {
        this.sequenceIndex = Number(this.fastForwardTo(CharCodes2.Questionmark));
      }
    }
    stateBeforeComment(c) {
      if (c === CharCodes2.Dash) {
        this.state = State.InCommentLike;
        this.currentSequence = Sequences.CommentEnd;
        this.sequenceIndex = 0;
        this.sectionStart = this.index + 1;
      } else if (this.xmlMode) {
        this.state = State.InDeclaration;
      } else if (c === CharCodes2.Gt) {
        this.cbs.oncomment(this.sectionStart, this.index, 0);
        this.state = State.Text;
        this.sectionStart = this.index + 1;
      } else {
        this.state = State.InSpecialComment;
      }
    }
    stateInSpecialComment(c) {
      if (c === CharCodes2.Gt || this.fastForwardTo(CharCodes2.Gt)) {
        this.cbs.oncomment(this.sectionStart, this.index, 0);
        this.state = State.Text;
        this.sectionStart = this.index + 1;
      }
    }
    startEntity() {
      this.baseState = this.state;
      this.state = State.InEntity;
      this.entityStart = this.index;
      this.entityDecoder.startEntity(this.xmlMode ? DecodingMode.Strict : this.baseState === State.Text || this.baseState === State.InSpecialTag ? DecodingMode.Legacy : DecodingMode.Attribute);
    }
    stateInEntity() {
      const indexInBuffer = this.index - this.offset;
      const length = this.entityDecoder.write(this.buffer, indexInBuffer);
      if (length >= 0) {
        this.state = this.baseState;
        if (length === 0) {
          this.index -= 1;
        }
      } else {
        if (indexInBuffer < this.buffer.length && this.buffer.charCodeAt(indexInBuffer) === CharCodes2.Amp) {
          this.state = this.baseState;
          this.index -= 1;
          return;
        }
        this.index = this.offset + this.buffer.length - 1;
      }
    }
    /**
     * Remove data that has already been consumed from the buffer.
     */
    cleanup() {
      if (this.running && this.sectionStart !== this.index) {
        if (this.state === State.Text || this.state === State.InPlainText || this.state === State.InSpecialTag && this.sequenceIndex === 0) {
          this.cbs.ontext(this.sectionStart, this.index);
          this.sectionStart = this.index;
        } else if (this.state === State.InAttributeValueDq || this.state === State.InAttributeValueSq || this.state === State.InAttributeValueNq) {
          this.cbs.onattribdata(this.sectionStart, this.index);
          this.sectionStart = this.index;
        }
      }
    }
    shouldContinue() {
      return this.index < this.buffer.length + this.offset && this.running;
    }
    /**
     * Iterates through the buffer, calling the function corresponding to the current state.
     *
     * States that are more likely to be hit are higher up, as a performance improvement.
     */
    parse() {
      while (this.shouldContinue()) {
        const c = this.buffer.charCodeAt(this.index - this.offset);
        switch (this.state) {
          case State.Text: {
            this.stateText(c);
            break;
          }
          case State.InPlainText: {
            this.index = this.buffer.length + this.offset - 1;
            break;
          }
          case State.SpecialStartSequence: {
            this.stateSpecialStartSequence(c);
            break;
          }
          case State.InSpecialTag: {
            this.stateInSpecialTag(c);
            break;
          }
          case State.CDATASequence: {
            this.stateCDATASequence(c);
            break;
          }
          case State.DeclarationSequence: {
            this.stateDeclarationSequence(c);
            break;
          }
          case State.InAttributeValueDq: {
            this.stateInAttributeValueDoubleQuotes(c);
            break;
          }
          case State.InAttributeName: {
            this.stateInAttributeName(c);
            break;
          }
          case State.InCommentLike: {
            this.stateInCommentLike(c);
            break;
          }
          case State.InSpecialComment: {
            this.stateInSpecialComment(c);
            break;
          }
          case State.BeforeAttributeName: {
            this.stateBeforeAttributeName(c);
            break;
          }
          case State.InTagName: {
            this.stateInTagName(c);
            break;
          }
          case State.InClosingTagName: {
            this.stateInClosingTagName(c);
            break;
          }
          case State.BeforeTagName: {
            this.stateBeforeTagName(c);
            break;
          }
          case State.AfterAttributeName: {
            this.stateAfterAttributeName(c);
            break;
          }
          case State.InAttributeValueSq: {
            this.stateInAttributeValueSingleQuotes(c);
            break;
          }
          case State.BeforeAttributeValue: {
            this.stateBeforeAttributeValue(c);
            break;
          }
          case State.BeforeClosingTagName: {
            this.stateBeforeClosingTagName(c);
            break;
          }
          case State.AfterClosingTagName: {
            this.stateAfterClosingTagName(c);
            break;
          }
          case State.InAttributeValueNq: {
            this.stateInAttributeValueNoQuotes(c);
            break;
          }
          case State.InSelfClosingTag: {
            this.stateInSelfClosingTag(c);
            break;
          }
          case State.InDeclaration: {
            this.stateInDeclaration(c);
            break;
          }
          case State.BeforeDeclaration: {
            this.stateBeforeDeclaration(c);
            break;
          }
          case State.BeforeComment: {
            this.stateBeforeComment(c);
            break;
          }
          case State.InProcessingInstruction: {
            this.stateInProcessingInstruction(c);
            break;
          }
          case State.InEntity: {
            this.stateInEntity();
            break;
          }
        }
        this.index++;
      }
      this.cleanup();
    }
    finish() {
      if (this.state === State.InEntity) {
        this.entityDecoder.end();
        this.state = this.baseState;
      }
      this.handleTrailingData();
      this.cbs.onend();
    }
    handleTrailingCommentLikeData(endIndex) {
      if (this.state !== State.InCommentLike) {
        return false;
      }
      if (this.currentSequence === Sequences.CdataEnd) {
        if (this.xmlMode) {
          if (this.sectionStart < endIndex) {
            this.cbs.oncdata(this.sectionStart, endIndex, 0);
          }
        } else {
          const cdataStart = this.sectionStart - Sequences.Cdata.length - 1;
          this.cbs.oncomment(cdataStart, endIndex, 0);
        }
      } else {
        const offset = this.xmlMode ? 0 : Math.min(this.sequenceIndex, Sequences.CommentEnd.length - 1);
        this.cbs.oncomment(this.sectionStart, endIndex, offset);
      }
      return true;
    }
    handleTrailingMarkupDeclaration(endIndex) {
      if (this.xmlMode) {
        switch (this.state) {
          case State.InSpecialComment:
          case State.BeforeComment:
          case State.CDATASequence:
          case State.DeclarationSequence:
          case State.InDeclaration: {
            this.cbs.ontext(this.sectionStart, endIndex);
            return true;
          }
          default: {
            return false;
          }
        }
      }
      switch (this.state) {
        case State.BeforeDeclaration:
        case State.InSpecialComment:
        case State.BeforeComment:
        case State.CDATASequence: {
          this.cbs.oncomment(this.sectionStart, endIndex, 0);
          return true;
        }
        case State.DeclarationSequence: {
          if (this.sequenceIndex !== Sequences.Doctype.length) {
            this.cbs.oncomment(this.sectionStart, endIndex, 0);
          }
          return true;
        }
        case State.InDeclaration: {
          return true;
        }
        default: {
          return false;
        }
      }
    }
    /** Handle any trailing data. */
    handleTrailingData() {
      const endIndex = this.buffer.length + this.offset;
      if (this.handleTrailingCommentLikeData(endIndex) || this.handleTrailingMarkupDeclaration(endIndex)) {
        return;
      }
      if (this.sectionStart >= endIndex) {
        return;
      }
      switch (this.state) {
        case State.InTagName:
        case State.BeforeAttributeName:
        case State.BeforeAttributeValue:
        case State.AfterAttributeName:
        case State.InAttributeName:
        case State.InAttributeValueSq:
        case State.InAttributeValueDq:
        case State.InAttributeValueNq:
        case State.InClosingTagName: {
          break;
        }
        default: {
          this.cbs.ontext(this.sectionStart, endIndex);
        }
      }
    }
    emitCodePoint(cp, consumed) {
      if (this.baseState !== State.Text && this.baseState !== State.InSpecialTag) {
        if (this.sectionStart < this.entityStart) {
          this.cbs.onattribdata(this.sectionStart, this.entityStart);
        }
        this.sectionStart = this.entityStart + consumed;
        this.index = this.sectionStart - 1;
        this.cbs.onattribentity(cp);
      } else {
        if (this.sectionStart < this.entityStart) {
          this.cbs.ontext(this.sectionStart, this.entityStart);
        }
        this.sectionStart = this.entityStart + consumed;
        this.index = this.sectionStart - 1;
        this.cbs.ontextentity(cp, this.sectionStart);
      }
    }
  };

  // ../unison-sources/node_modules/htmlparser2/dist/Parser.js
  var { fromCodePoint } = String;
  var formTags = /* @__PURE__ */ new Set([
    "input",
    "option",
    "optgroup",
    "select",
    "button",
    "datalist",
    "textarea"
  ]);
  var pTag = /* @__PURE__ */ new Set(["p"]);
  var headingTags = /* @__PURE__ */ new Set(["h1", "h2", "h3", "h4", "h5", "h6", "p"]);
  var tableSectionTags = /* @__PURE__ */ new Set(["thead", "tbody"]);
  var ddtTags = /* @__PURE__ */ new Set(["dd", "dt"]);
  var rtpTags = /* @__PURE__ */ new Set(["rt", "rp"]);
  var openImpliesClose = /* @__PURE__ */ new Map([
    ["tr", /* @__PURE__ */ new Set(["tr", "th", "td"])],
    ["th", /* @__PURE__ */ new Set(["th"])],
    ["td", /* @__PURE__ */ new Set(["thead", "th", "td"])],
    ["body", /* @__PURE__ */ new Set(["head", "link", "script"])],
    ["a", /* @__PURE__ */ new Set(["a"])],
    ["li", /* @__PURE__ */ new Set(["li"])],
    ["p", pTag],
    ["h1", headingTags],
    ["h2", headingTags],
    ["h3", headingTags],
    ["h4", headingTags],
    ["h5", headingTags],
    ["h6", headingTags],
    ["select", formTags],
    ["input", formTags],
    ["output", formTags],
    ["button", formTags],
    ["datalist", formTags],
    ["textarea", formTags],
    ["option", /* @__PURE__ */ new Set(["option"])],
    ["optgroup", /* @__PURE__ */ new Set(["optgroup", "option"])],
    ["dd", ddtTags],
    ["dt", ddtTags],
    ["address", pTag],
    ["article", pTag],
    ["aside", pTag],
    ["blockquote", pTag],
    ["details", pTag],
    ["div", pTag],
    ["dl", pTag],
    ["fieldset", pTag],
    ["figcaption", pTag],
    ["figure", pTag],
    ["footer", pTag],
    ["form", pTag],
    ["header", pTag],
    ["hr", pTag],
    ["main", pTag],
    ["nav", pTag],
    ["ol", pTag],
    ["pre", pTag],
    ["section", pTag],
    ["table", pTag],
    ["ul", pTag],
    ["rt", rtpTags],
    ["rp", rtpTags],
    ["tbody", tableSectionTags],
    ["tfoot", tableSectionTags]
  ]);
  var DOCUMENT_TYPE = "doctype";
  var voidElements = /* @__PURE__ */ new Set([
    "area",
    "base",
    "basefont",
    "br",
    "col",
    "command",
    "embed",
    "frame",
    "hr",
    "img",
    "input",
    "isindex",
    "keygen",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr"
  ]);
  var foreignContextElements = /* @__PURE__ */ new Set(["math", "svg"]);
  var htmlIntegrationElements = /* @__PURE__ */ new Set([
    "mi",
    "mo",
    "mn",
    "ms",
    "mtext",
    "annotation-xml",
    "foreignObject",
    "desc",
    "title"
  ]);
  var svgTagNameAdjustments = /* @__PURE__ */ new Map([
    ["altglyph", "altGlyph"],
    ["altglyphdef", "altGlyphDef"],
    ["altglyphitem", "altGlyphItem"],
    ["animatecolor", "animateColor"],
    ["animatemotion", "animateMotion"],
    ["animatetransform", "animateTransform"],
    ["clippath", "clipPath"],
    ["feblend", "feBlend"],
    ["fecolormatrix", "feColorMatrix"],
    ["fecomponenttransfer", "feComponentTransfer"],
    ["fecomposite", "feComposite"],
    ["feconvolvematrix", "feConvolveMatrix"],
    ["fediffuselighting", "feDiffuseLighting"],
    ["fedisplacementmap", "feDisplacementMap"],
    ["fedistantlight", "feDistantLight"],
    ["fedropshadow", "feDropShadow"],
    ["feflood", "feFlood"],
    ["fefunca", "feFuncA"],
    ["fefuncb", "feFuncB"],
    ["fefuncg", "feFuncG"],
    ["fefuncr", "feFuncR"],
    ["fegaussianblur", "feGaussianBlur"],
    ["feimage", "feImage"],
    ["femerge", "feMerge"],
    ["femergenode", "feMergeNode"],
    ["femorphology", "feMorphology"],
    ["feoffset", "feOffset"],
    ["fepointlight", "fePointLight"],
    ["fespecularlighting", "feSpecularLighting"],
    ["fespotlight", "feSpotLight"],
    ["fetile", "feTile"],
    ["feturbulence", "feTurbulence"],
    ["foreignobject", "foreignObject"],
    ["glyphref", "glyphRef"],
    ["lineargradient", "linearGradient"],
    ["radialgradient", "radialGradient"],
    ["textpath", "textPath"]
  ]);
  var ForeignContext;
  (function(ForeignContext2) {
    ForeignContext2[ForeignContext2["None"] = 0] = "None";
    ForeignContext2[ForeignContext2["Svg"] = 1] = "Svg";
    ForeignContext2[ForeignContext2["MathML"] = 2] = "MathML";
  })(ForeignContext || (ForeignContext = {}));
  var reNameEnd = /\s|\//;
  var Parser = class {
    constructor(cbs, options = {}) {
      __publicField(this, "options");
      /** The start index of the last event. */
      __publicField(this, "startIndex", 0);
      /** The end index of the last event. */
      __publicField(this, "endIndex", 0);
      /**
       * Store the start index of the current open tag,
       * so we can update the start index for attributes.
       */
      __publicField(this, "openTagStart", 0);
      __publicField(this, "tagname", "");
      __publicField(this, "attribname", "");
      __publicField(this, "attribvalue", "");
      __publicField(this, "attribs", null);
      __publicField(this, "stack", []);
      __publicField(this, "foreignContext");
      __publicField(this, "cbs");
      __publicField(this, "lowerCaseTagNames");
      __publicField(this, "lowerCaseAttributeNames");
      __publicField(this, "recognizeSelfClosing");
      /** We are parsing HTML. Inverse of the `xmlMode` option. */
      __publicField(this, "htmlMode");
      __publicField(this, "tokenizer");
      __publicField(this, "buffers", []);
      __publicField(this, "bufferOffset", 0);
      /** The index of the last written buffer. Used when resuming after a `pause()`. */
      __publicField(this, "writeIndex", 0);
      /** Indicates whether the parser has finished running / `.end` has been called. */
      __publicField(this, "ended", false);
      this.options = options;
      this.cbs = cbs ?? {};
      this.htmlMode = !this.options.xmlMode;
      this.lowerCaseTagNames = options.lowerCaseTags ?? this.htmlMode;
      this.lowerCaseAttributeNames = options.lowerCaseAttributeNames ?? this.htmlMode;
      this.recognizeSelfClosing = options.recognizeSelfClosing ?? !this.htmlMode;
      this.tokenizer = new (options.Tokenizer ?? Tokenizer)(this.options, this);
      this.foreignContext = [ForeignContext.None];
      this.cbs.onparserinit?.(this);
    }
    // Tokenizer event handlers
    /**
     * @param start Start index for the current parser event.
     * @param endIndex End index for the current parser event.
     * @internal
     */
    ontext(start, endIndex) {
      const data = this.getSlice(start, endIndex);
      this.endIndex = endIndex - 1;
      this.cbs.ontext?.(data);
      this.startIndex = endIndex;
    }
    /**
     * @param cp Current Unicode code point.
     * @param endIndex End index for the current parser event.
     * @internal
     */
    ontextentity(cp, endIndex) {
      this.endIndex = endIndex - 1;
      this.cbs.ontext?.(fromCodePoint(cp));
      this.startIndex = endIndex;
    }
    /** @internal */
    isInForeignContext() {
      return this.foreignContext[0] !== ForeignContext.None;
    }
    /**
     * Checks if the current tag is a void element. Override this if you want
     * to specify your own additional void elements.
     * @param name Name of the pseudo selector.
     */
    isVoidElement(name) {
      return this.htmlMode && voidElements.has(name);
    }
    /**
     * Read a tag name from the buffer.
     *
     * When `lowerCaseTagNames` is enabled (the default in HTML mode), the name
     * is lowercased and may be adjusted for SVG casing or the `image` → `img`
     * alias.
     * @param start Start index of the tag name in the buffer.
     * @param endIndex End index of the tag name in the buffer.
     */
    readTagName(start, endIndex) {
      const name = this.lowerCaseTagNames ? this.getSlice(start, endIndex).toLowerCase() : this.getSlice(start, endIndex);
      if (!(this.lowerCaseTagNames && this.htmlMode)) {
        return name;
      }
      if (this.foreignContext[0] === ForeignContext.Svg) {
        return svgTagNameAdjustments.get(name) ?? name;
      }
      if (this.foreignContext.length > 1) {
        const adjusted = svgTagNameAdjustments.get(name);
        if (adjusted !== void 0 && this.stack.includes(adjusted)) {
          return adjusted;
        }
      }
      if (!this.isInForeignContext()) {
        return name === "image" ? "img" : name;
      }
      return name;
    }
    /**
     * @param start Start index for the current parser event.
     * @param endIndex End index for the current parser event.
     * @internal
     */
    onopentagname(start, endIndex) {
      this.endIndex = endIndex;
      this.emitOpenTag(this.readTagName(start, endIndex));
    }
    emitOpenTag(name) {
      this.openTagStart = this.startIndex;
      this.tagname = name;
      if (this.htmlMode && name === "form" && this.stack.includes("form")) {
        this.tagname = "";
        return;
      }
      const impliesClose = this.htmlMode && openImpliesClose.get(name);
      if (impliesClose) {
        while (this.stack.length > 0 && impliesClose.has(this.stack[0])) {
          this.popElement(true);
        }
      }
      if (!this.isVoidElement(name)) {
        this.stack.unshift(name);
        if (this.htmlMode) {
          if (name === "svg") {
            this.foreignContext.unshift(ForeignContext.Svg);
          } else if (name === "math") {
            this.foreignContext.unshift(ForeignContext.MathML);
          } else if (htmlIntegrationElements.has(name)) {
            this.foreignContext.unshift(ForeignContext.None);
          }
        }
      }
      this.cbs.onopentagname?.(name);
      if (this.cbs.onopentag)
        this.attribs = {};
    }
    endOpenTag(isImplied) {
      this.startIndex = this.openTagStart;
      if (this.attribs) {
        this.cbs.onopentag?.(this.tagname, this.attribs, isImplied);
        this.attribs = null;
      }
      if (this.cbs.onclosetag && this.isVoidElement(this.tagname)) {
        this.cbs.onclosetag(this.tagname, true);
      }
      this.tagname = "";
    }
    /**
     * @param endIndex End index for the current parser event.
     * @internal
     */
    onopentagend(endIndex) {
      this.endIndex = endIndex;
      this.endOpenTag(false);
      this.startIndex = endIndex + 1;
    }
    /**
     * @param start Start index for the current parser event.
     * @param endIndex End index for the current parser event.
     * @internal
     */
    onclosetag(start, endIndex) {
      this.endIndex = endIndex;
      const name = this.readTagName(start, endIndex);
      if (!this.isVoidElement(name)) {
        const pos = this.stack.indexOf(name);
        if (pos !== -1) {
          for (let index = 0; index < pos; index++) {
            this.popElement(true);
          }
          this.popElement(false);
        } else if (this.htmlMode && name === "p") {
          this.emitOpenTag("p");
          this.closeCurrentTag(true);
        }
      } else if (this.htmlMode && name === "br") {
        this.cbs.onopentagname?.("br");
        this.cbs.onopentag?.("br", {}, true);
        this.cbs.onclosetag?.("br", false);
      }
      this.startIndex = endIndex + 1;
    }
    /**
     * @param endIndex End index for the current parser event.
     * @internal
     */
    onselfclosingtag(endIndex) {
      this.endIndex = endIndex;
      if (this.recognizeSelfClosing || this.isInForeignContext()) {
        this.closeCurrentTag(false);
        this.startIndex = endIndex + 1;
      } else {
        this.onopentagend(endIndex);
      }
    }
    /**
     * Pop the top element off the stack, emit a close event, and maintain
     * the foreign context stack.
     * @param implied Whether this close is implied (not from an explicit end tag).
     */
    popElement(implied) {
      const element = this.stack.shift();
      if (this.htmlMode && (foreignContextElements.has(element) || htmlIntegrationElements.has(element))) {
        this.foreignContext.shift();
      }
      this.cbs.onclosetag?.(element, implied);
    }
    closeCurrentTag(isOpenImplied) {
      const name = this.tagname;
      this.endOpenTag(isOpenImplied);
      if (this.stack[0] === name) {
        this.popElement(!isOpenImplied);
      }
    }
    /**
     * @param start Start index for the current parser event.
     * @param endIndex End index for the current parser event.
     * @internal
     */
    onattribname(start, endIndex) {
      this.startIndex = start;
      const name = this.getSlice(start, endIndex);
      this.attribname = this.lowerCaseAttributeNames ? name.toLowerCase() : name;
    }
    /**
     * @param start Start index for the current parser event.
     * @param endIndex End index for the current parser event.
     * @internal
     */
    onattribdata(start, endIndex) {
      this.attribvalue += this.getSlice(start, endIndex);
    }
    /**
     * @param cp Current Unicode code point.
     * @internal
     */
    onattribentity(cp) {
      this.attribvalue += fromCodePoint(cp);
    }
    /**
     * @param quote Quote type used for the current attribute.
     * @param endIndex End index for the current parser event.
     * @internal
     */
    onattribend(quote, endIndex) {
      this.endIndex = endIndex;
      this.cbs.onattribute?.(this.attribname, this.attribvalue, quote === QuoteType.Double ? '"' : quote === QuoteType.Single ? "'" : quote === QuoteType.NoValue ? void 0 : null);
      if (this.attribs && !Object.hasOwn(this.attribs, this.attribname)) {
        this.attribs[this.attribname] = this.attribvalue;
      }
      this.attribvalue = "";
    }
    getInstructionName(value) {
      const index = value.search(reNameEnd);
      let name = index < 0 ? value : value.substr(0, index);
      if (this.lowerCaseTagNames) {
        name = name.toLowerCase();
      }
      return name;
    }
    /**
     * @param start Start index for the current parser event.
     * @param endIndex End index for the current parser event.
     * @internal
     */
    ondeclaration(start, endIndex) {
      this.endIndex = endIndex;
      const value = this.getSlice(start, endIndex);
      if (this.cbs.onprocessinginstruction) {
        const name = this.htmlMode ? this.lowerCaseTagNames ? DOCUMENT_TYPE : value.slice(0, DOCUMENT_TYPE.length) : this.getInstructionName(value);
        this.cbs.onprocessinginstruction(`!${name}`, `!${value}`);
      }
      this.startIndex = endIndex + 1;
    }
    /**
     * @param start Start index for the current parser event.
     * @param endIndex End index for the current parser event.
     * @internal
     */
    onprocessinginstruction(start, endIndex) {
      this.endIndex = endIndex;
      const value = this.getSlice(start, endIndex);
      if (this.cbs.onprocessinginstruction) {
        const name = this.getInstructionName(value);
        this.cbs.onprocessinginstruction(`?${name}`, `?${value}`);
      }
      this.startIndex = endIndex + 1;
    }
    /**
     * @param start Start index for the current parser event.
     * @param endIndex End index for the current parser event.
     * @param offset Offset applied when computing parser indices.
     * @internal
     */
    oncomment(start, endIndex, offset) {
      this.endIndex = endIndex;
      this.cbs.oncomment?.(this.getSlice(start, endIndex - offset));
      this.cbs.oncommentend?.();
      this.startIndex = endIndex + 1;
    }
    /**
     * @param start Start index for the current parser event.
     * @param endIndex End index for the current parser event.
     * @param offset Offset applied when computing parser indices.
     * @internal
     */
    oncdata(start, endIndex, offset) {
      this.endIndex = endIndex;
      const value = this.getSlice(start, endIndex - offset);
      if (!this.htmlMode || this.options.recognizeCDATA) {
        this.cbs.oncdatastart?.();
        this.cbs.ontext?.(value);
        this.cbs.oncdataend?.();
      } else if (this.isInForeignContext()) {
        this.cbs.ontext?.(value);
      } else {
        this.cbs.oncomment?.(`[CDATA[${value}]]`);
        this.cbs.oncommentend?.();
      }
      this.startIndex = endIndex + 1;
    }
    /** @internal */
    onend() {
      if (this.cbs.onclosetag) {
        this.endIndex = this.startIndex;
        for (let index = 0; index < this.stack.length; index++) {
          this.cbs.onclosetag(this.stack[index], true);
        }
      }
      this.cbs.onend?.();
    }
    /**
     * Resets the parser to a blank state, ready to parse a new HTML document
     */
    reset() {
      this.cbs.onreset?.();
      this.tokenizer.reset();
      this.tagname = "";
      this.attribname = "";
      this.attribvalue = "";
      this.attribs = null;
      this.stack.length = 0;
      this.startIndex = 0;
      this.endIndex = 0;
      this.cbs.onparserinit?.(this);
      this.buffers.length = 0;
      this.foreignContext.length = 0;
      this.foreignContext.unshift(ForeignContext.None);
      this.bufferOffset = 0;
      this.writeIndex = 0;
      this.ended = false;
    }
    /**
     * Resets the parser, then parses a complete document and
     * pushes it to the handler.
     * @param data Document to parse.
     */
    parseComplete(data) {
      this.reset();
      this.end(data);
    }
    getSlice(start, end) {
      if (start === end) {
        return "";
      }
      while (start - this.bufferOffset >= this.buffers[0].length) {
        this.shiftBuffer();
      }
      let slice = this.buffers[0].slice(start - this.bufferOffset, end - this.bufferOffset);
      while (end - this.bufferOffset > this.buffers[0].length) {
        this.shiftBuffer();
        slice += this.buffers[0].slice(0, end - this.bufferOffset);
      }
      return slice;
    }
    shiftBuffer() {
      this.bufferOffset += this.buffers[0].length;
      this.writeIndex--;
      this.buffers.shift();
    }
    /**
     * Parses a chunk of data and calls the corresponding callbacks.
     * @param chunk Chunk to parse.
     */
    write(chunk) {
      if (this.ended) {
        this.cbs.onerror?.(new Error(".write() after done!"));
        return;
      }
      this.buffers.push(chunk);
      if (this.tokenizer.running) {
        this.tokenizer.write(chunk);
        this.writeIndex++;
      }
    }
    /**
     * Parses the end of the buffer and clears the stack, calls onend.
     * @param chunk Optional final chunk to parse.
     */
    end(chunk) {
      if (this.ended) {
        this.cbs.onerror?.(new Error(".end() after done!"));
        return;
      }
      if (chunk)
        this.write(chunk);
      this.ended = true;
      this.tokenizer.end();
    }
    /**
     * Pauses parsing. The parser won't emit events until `resume` is called.
     */
    pause() {
      this.tokenizer.pause();
    }
    /**
     * Resumes parsing after `pause` was called.
     */
    resume() {
      this.tokenizer.resume();
      while (this.tokenizer.running && this.writeIndex < this.buffers.length) {
        this.tokenizer.write(this.buffers[this.writeIndex++]);
      }
      if (this.ended)
        this.tokenizer.end();
    }
  };

  // ../unison-sources/node_modules/domelementtype/dist/index.js
  var ElementType;
  (function(ElementType2) {
    ElementType2["Root"] = "root";
    ElementType2["Text"] = "text";
    ElementType2["Directive"] = "directive";
    ElementType2["Comment"] = "comment";
    ElementType2["Script"] = "script";
    ElementType2["Style"] = "style";
    ElementType2["Tag"] = "tag";
    ElementType2["CDATA"] = "cdata";
    ElementType2["Doctype"] = "doctype";
  })(ElementType || (ElementType = {}));
  function isTag(element) {
    return element.type === ElementType.Tag || element.type === ElementType.Script || element.type === ElementType.Style;
  }
  var Root = ElementType.Root;
  var Text = ElementType.Text;
  var Directive = ElementType.Directive;
  var Comment = ElementType.Comment;
  var Script = ElementType.Script;
  var Style = ElementType.Style;
  var Tag = ElementType.Tag;
  var CDATA = ElementType.CDATA;
  var Doctype = ElementType.Doctype;

  // ../unison-sources/node_modules/domhandler/dist/node.js
  var Node = class {
    constructor() {
      /** Parent of the node */
      __publicField(this, "parent", null);
      /** Previous sibling */
      __publicField(this, "prev", null);
      /** Next sibling */
      __publicField(this, "next", null);
      /** The start index of the node. Requires `withStartIndices` on the handler to be `true. */
      __publicField(this, "startIndex", null);
      /** The end index of the node. Requires `withEndIndices` on the handler to be `true. */
      __publicField(this, "endIndex", null);
    }
    // Read-write aliases for properties
    /**
     * Same as {@link parent}.
     * [DOM spec](https://dom.spec.whatwg.org)-compatible alias.
     */
    get parentNode() {
      return this.parent;
    }
    set parentNode(parent) {
      this.parent = parent;
    }
    /**
     * Same as {@link prev}.
     * [DOM spec](https://dom.spec.whatwg.org)-compatible alias.
     */
    get previousSibling() {
      return this.prev;
    }
    set previousSibling(previous) {
      this.prev = previous;
    }
    /**
     * Same as {@link next}.
     * [DOM spec](https://dom.spec.whatwg.org)-compatible alias.
     */
    get nextSibling() {
      return this.next;
    }
    set nextSibling(next) {
      this.next = next;
    }
    /**
     * Clone this node, and optionally its children.
     * @param recursive Clone child nodes as well.
     * @returns A clone of the node.
     */
    cloneNode(recursive = false) {
      return cloneNode(this, recursive);
    }
  };
  var DataNode = class extends Node {
    /**
     * @param data The content of the data node
     */
    constructor(data) {
      super();
      __publicField(this, "data");
      this.data = data;
    }
    /**
     * Same as {@link data}.
     * [DOM spec](https://dom.spec.whatwg.org)-compatible alias.
     */
    get nodeValue() {
      return this.data;
    }
    set nodeValue(data) {
      this.data = data;
    }
  };
  var Text2 = class extends DataNode {
    constructor() {
      super(...arguments);
      __publicField(this, "type", ElementType.Text);
    }
    get nodeType() {
      return 3;
    }
  };
  var Comment2 = class extends DataNode {
    constructor() {
      super(...arguments);
      __publicField(this, "type", ElementType.Comment);
    }
    get nodeType() {
      return 8;
    }
  };
  var ProcessingInstruction = class extends DataNode {
    constructor(name, data) {
      super(data);
      __publicField(this, "type", ElementType.Directive);
      __publicField(this, "name");
      /** If this is a doctype, the document type name (parse5 only). */
      __publicField(this, "x-name");
      /** If this is a doctype, the document type public identifier (parse5 only). */
      __publicField(this, "x-publicId");
      /** If this is a doctype, the document type system identifier (parse5 only). */
      __publicField(this, "x-systemId");
      this.name = name;
    }
    get nodeType() {
      return 1;
    }
  };
  var NodeWithChildren = class extends Node {
    /**
     * @param children Children of the node. Only certain node types can have children.
     */
    constructor(children) {
      super();
      __publicField(this, "children");
      this.children = children;
    }
    // Aliases
    /** First child of the node. */
    get firstChild() {
      return this.children[0] ?? null;
    }
    /** Last child of the node. */
    get lastChild() {
      return this.children.length > 0 ? this.children[this.children.length - 1] : null;
    }
    /**
     * Same as {@link children}.
     * [DOM spec](https://dom.spec.whatwg.org)-compatible alias.
     */
    get childNodes() {
      return this.children;
    }
    set childNodes(children) {
      this.children = children;
    }
  };
  var CDATA2 = class extends NodeWithChildren {
    constructor() {
      super(...arguments);
      __publicField(this, "type", ElementType.CDATA);
    }
    get nodeType() {
      return 4;
    }
  };
  var Document = class extends NodeWithChildren {
    constructor() {
      super(...arguments);
      __publicField(this, "type", ElementType.Root);
    }
    get nodeType() {
      return 9;
    }
  };
  var Element = class extends NodeWithChildren {
    /**
     * @param name Name of the tag, eg. `div`, `span`.
     * @param attribs Object mapping attribute names to attribute values.
     * @param children Children of the node.
     * @param type Node type used for the new node instance.
     */
    constructor(name, attribs, children = [], type = name === "script" ? ElementType.Script : name === "style" ? ElementType.Style : ElementType.Tag) {
      super(children);
      __publicField(this, "name");
      __publicField(this, "attribs");
      __publicField(this, "type");
      /** Element namespace (parse5 only). */
      __publicField(this, "namespace");
      /** Element attribute namespaces (parse5 only). */
      __publicField(this, "x-attribsNamespace");
      /** Element attribute namespace-related prefixes (parse5 only). */
      __publicField(this, "x-attribsPrefix");
      this.name = name;
      this.attribs = attribs;
      this.type = type;
    }
    get nodeType() {
      return 1;
    }
    // DOM Level 1 aliases
    /**
     * Same as {@link name}.
     * [DOM spec](https://dom.spec.whatwg.org)-compatible alias.
     */
    get tagName() {
      return this.name;
    }
    set tagName(name) {
      this.name = name;
    }
    get attributes() {
      return Object.keys(this.attribs).map((name) => ({
        name,
        value: this.attribs[name],
        namespace: this["x-attribsNamespace"]?.[name],
        prefix: this["x-attribsPrefix"]?.[name]
      }));
    }
  };
  function isTag2(node) {
    return isTag(node);
  }
  function isCDATA(node) {
    return node.type === ElementType.CDATA;
  }
  function isText(node) {
    return node.type === ElementType.Text;
  }
  function isComment(node) {
    return node.type === ElementType.Comment;
  }
  function isDirective(node) {
    return node.type === ElementType.Directive;
  }
  function isDocument(node) {
    return node.type === ElementType.Root;
  }
  function hasChildren(node) {
    return Object.hasOwn(node, "children");
  }
  function cloneNode(node, recursive = false) {
    let result;
    if (isText(node)) {
      result = new Text2(node.data);
    } else if (isComment(node)) {
      result = new Comment2(node.data);
    } else if (isTag2(node)) {
      const children = recursive ? cloneChildren(node.children) : [];
      const clone = new Element(node.name, { ...node.attribs }, children);
      for (const child of children) {
        child.parent = clone;
      }
      if (node.namespace != null) {
        clone.namespace = node.namespace;
      }
      if (node["x-attribsNamespace"]) {
        clone["x-attribsNamespace"] = { ...node["x-attribsNamespace"] };
      }
      if (node["x-attribsPrefix"]) {
        clone["x-attribsPrefix"] = { ...node["x-attribsPrefix"] };
      }
      result = clone;
    } else if (isCDATA(node)) {
      const children = recursive ? cloneChildren(node.children) : [];
      const clone = new CDATA2(children);
      for (const child of children) {
        child.parent = clone;
      }
      result = clone;
    } else if (isDocument(node)) {
      const children = recursive ? cloneChildren(node.children) : [];
      const clone = new Document(children);
      for (const child of children) {
        child.parent = clone;
      }
      if (node["x-mode"]) {
        clone["x-mode"] = node["x-mode"];
      }
      result = clone;
    } else if (isDirective(node)) {
      const instruction = new ProcessingInstruction(node.name, node.data);
      if (node["x-name"] != null) {
        instruction["x-name"] = node["x-name"];
        instruction["x-publicId"] = node["x-publicId"];
        instruction["x-systemId"] = node["x-systemId"];
      }
      result = instruction;
    } else {
      throw new Error(`Not implemented yet: ${node.type}`);
    }
    result.startIndex = node.startIndex;
    result.endIndex = node.endIndex;
    if (node.sourceCodeLocation != null) {
      result.sourceCodeLocation = node.sourceCodeLocation;
    }
    return result;
  }
  function cloneChildren(childs) {
    const children = childs.map((child) => cloneNode(child, true));
    for (let index = 1; index < children.length; index++) {
      children[index].prev = children[index - 1];
      children[index - 1].next = children[index];
    }
    return children;
  }

  // ../unison-sources/node_modules/domhandler/dist/index.js
  var defaultOptions = {
    withStartIndices: false,
    withEndIndices: false,
    xmlMode: false
  };
  var DomHandler = class {
    /**
     * @param callback Called once parsing has completed.
     * @param options Settings for the handler.
     * @param elementCB Callback whenever a tag is closed.
     */
    constructor(callback, options, elementCB) {
      /** The elements of the DOM */
      __publicField(this, "dom", []);
      /** The root element for the DOM */
      __publicField(this, "root", new Document(this.dom));
      /** Called once parsing has completed. */
      __publicField(this, "callback");
      /** Settings for the handler. */
      __publicField(this, "options");
      /** Callback whenever a tag is closed. */
      __publicField(this, "elementCB");
      /** Indicated whether parsing has been completed. */
      __publicField(this, "done", false);
      /** Stack of open tags. */
      __publicField(this, "tagStack", [this.root]);
      /** A data node that is still being written to. */
      __publicField(this, "lastNode", null);
      /** Reference to the parser instance. Used for location information. */
      __publicField(this, "parser", null);
      if (typeof options === "function") {
        elementCB = options;
        options = defaultOptions;
      }
      if (typeof callback === "object") {
        options = callback;
        callback = void 0;
      }
      this.callback = callback ?? null;
      this.options = options ?? defaultOptions;
      this.elementCB = elementCB ?? null;
    }
    onparserinit(parser) {
      this.parser = parser;
    }
    // Resets the handler back to starting state
    onreset() {
      this.dom = [];
      this.root = new Document(this.dom);
      this.done = false;
      this.tagStack = [this.root];
      this.lastNode = null;
      this.parser = null;
    }
    // Signals the handler that parsing is done
    onend() {
      if (this.done)
        return;
      this.done = true;
      this.parser = null;
      this.handleCallback(null);
    }
    onerror(error) {
      this.handleCallback(error);
    }
    onclosetag() {
      this.lastNode = null;
      const element = this.tagStack.pop();
      if (this.options.withEndIndices && this.parser) {
        element.endIndex = this.parser.endIndex;
      }
      if (this.elementCB)
        this.elementCB(element);
    }
    onopentag(name, attribs) {
      const type = this.options.xmlMode ? ElementType.Tag : void 0;
      const element = new Element(name, attribs, void 0, type);
      this.addNode(element);
      this.tagStack.push(element);
    }
    ontext(data) {
      const { lastNode } = this;
      if (lastNode && lastNode.type === ElementType.Text) {
        lastNode.data += data;
        if (this.options.withEndIndices && this.parser) {
          lastNode.endIndex = this.parser.endIndex;
        }
      } else {
        const node = new Text2(data);
        this.addNode(node);
        this.lastNode = node;
      }
    }
    oncomment(data) {
      if (this.lastNode && this.lastNode.type === ElementType.Comment) {
        this.lastNode.data += data;
        return;
      }
      const node = new Comment2(data);
      this.addNode(node);
      this.lastNode = node;
    }
    oncommentend() {
      this.lastNode = null;
    }
    oncdatastart() {
      const text = new Text2("");
      const node = new CDATA2([text]);
      this.addNode(node);
      text.parent = node;
      this.lastNode = text;
    }
    oncdataend() {
      this.lastNode = null;
    }
    onprocessinginstruction(name, data) {
      const node = new ProcessingInstruction(name, data);
      this.addNode(node);
    }
    handleCallback(error) {
      if (typeof this.callback === "function") {
        this.callback(error, this.dom);
      } else if (error) {
        throw error;
      }
    }
    addNode(node) {
      const parent = this.tagStack[this.tagStack.length - 1];
      const previousSibling = parent.children[parent.children.length - 1];
      if (this.options.withStartIndices && this.parser) {
        node.startIndex = this.parser.startIndex;
      }
      if (this.options.withEndIndices && this.parser) {
        node.endIndex = this.parser.endIndex;
      }
      parent.children.push(node);
      if (previousSibling) {
        node.prev = previousSibling;
        previousSibling.next = node;
      }
      node.parent = parent;
      this.lastNode = null;
    }
  };

  // ../unison-sources/node_modules/domutils/dist/index.js
  var dist_exports2 = {};
  __export(dist_exports2, {
    DocumentPosition: () => DocumentPosition,
    append: () => append,
    appendChild: () => appendChild,
    compareDocumentPosition: () => compareDocumentPosition,
    existsOne: () => existsOne,
    filter: () => filter,
    find: () => find,
    findAll: () => findAll,
    findOne: () => findOne,
    getAttributeValue: () => getAttributeValue,
    getChildren: () => getChildren,
    getElementById: () => getElementById,
    getElements: () => getElements,
    getElementsByClassName: () => getElementsByClassName,
    getElementsByTagName: () => getElementsByTagName,
    getElementsByTagType: () => getElementsByTagType,
    getFeed: () => getFeed,
    getInnerHTML: () => getInnerHTML,
    getName: () => getName,
    getOuterHTML: () => getOuterHTML,
    getParent: () => getParent,
    getSiblings: () => getSiblings,
    getText: () => getText,
    hasAttrib: () => hasAttrib,
    innerText: () => innerText,
    nextElementSibling: () => nextElementSibling,
    prepend: () => prepend,
    prependChild: () => prependChild,
    prevElementSibling: () => prevElementSibling,
    removeElement: () => removeElement,
    removeSubsets: () => removeSubsets,
    replaceElement: () => replaceElement,
    testElement: () => testElement,
    textContent: () => textContent,
    uniqueSort: () => uniqueSort
  });

  // ../unison-sources/node_modules/domutils/dist/querying.js
  function filter(test, node, recurse = true, limit = Number.POSITIVE_INFINITY) {
    return find(test, Array.isArray(node) ? node : [node], recurse, limit);
  }
  function find(test, nodes, recurse, limit) {
    const result = [];
    const nodeStack = [Array.isArray(nodes) ? nodes : [nodes]];
    const indexStack = [0];
    for (; ; ) {
      if (indexStack[0] >= nodeStack[0].length) {
        if (indexStack.length === 1) {
          return result;
        }
        nodeStack.shift();
        indexStack.shift();
        continue;
      }
      const element = nodeStack[0][indexStack[0]++];
      if (test(element)) {
        result.push(element);
        if (--limit <= 0)
          return result;
      }
      if (recurse && hasChildren(element) && element.children.length > 0) {
        indexStack.unshift(0);
        nodeStack.unshift(element.children);
      }
    }
  }
  function findOne(test, nodes, recurse = true) {
    const searchedNodes = Array.isArray(nodes) ? nodes : [nodes];
    for (const node of searchedNodes) {
      if (isTag2(node) && test(node)) {
        return node;
      }
      if (recurse && hasChildren(node) && node.children.length > 0) {
        const found = findOne(test, node.children, true);
        if (found)
          return found;
      }
    }
    return null;
  }
  function existsOne(test, nodes) {
    return (Array.isArray(nodes) ? nodes : [nodes]).some((node) => isTag2(node) && test(node) || hasChildren(node) && existsOne(test, node.children));
  }
  function findAll(test, nodes) {
    const result = [];
    const nodeStack = [Array.isArray(nodes) ? nodes : [nodes]];
    const indexStack = [0];
    for (; ; ) {
      if (indexStack[0] >= nodeStack[0].length) {
        if (nodeStack.length === 1) {
          return result;
        }
        nodeStack.shift();
        indexStack.shift();
        continue;
      }
      const element = nodeStack[0][indexStack[0]++];
      if (isTag2(element) && test(element))
        result.push(element);
      if (hasChildren(element) && element.children.length > 0) {
        indexStack.unshift(0);
        nodeStack.unshift(element.children);
      }
    }
  }

  // ../unison-sources/node_modules/domutils/dist/legacy.js
  var Checks = {
    tag_name(name) {
      if (typeof name === "function") {
        return (element) => isTag2(element) && name(element.name);
      }
      if (name === "*") {
        return isTag2;
      }
      return (element) => isTag2(element) && element.name === name;
    },
    tag_type(type) {
      if (typeof type === "function") {
        return (element) => type(element.type);
      }
      return (element) => element.type === type;
    },
    tag_contains(data) {
      if (typeof data === "function") {
        return (element) => isText(element) && data(element.data);
      }
      return (element) => isText(element) && element.data === data;
    }
  };
  function getAttribCheck(attrib, value) {
    if (typeof value === "function") {
      return (element) => isTag2(element) && value(element.attribs[attrib]);
    }
    return (element) => isTag2(element) && element.attribs[attrib] === value;
  }
  function combineFuncs(a, b) {
    return (element) => a(element) || b(element);
  }
  function compileTest(options) {
    const funcs = Object.keys(options).map((key) => {
      const value = options[key];
      return Object.hasOwn(Checks, key) ? Checks[key](value) : getAttribCheck(key, value);
    });
    return funcs.length === 0 ? null : funcs.reduce(combineFuncs);
  }
  function testElement(options, node) {
    const test = compileTest(options);
    return test ? test(node) : true;
  }
  function getElements(options, nodes, recurse, limit = Number.POSITIVE_INFINITY) {
    const test = compileTest(options);
    return test ? filter(test, nodes, recurse, limit) : [];
  }
  function getElementById(id, nodes, recurse = true) {
    if (!Array.isArray(nodes))
      nodes = [nodes];
    return findOne(getAttribCheck("id", id), nodes, recurse);
  }
  function getElementsByTagName(tagName, nodes, recurse = true, limit = Number.POSITIVE_INFINITY) {
    return filter(Checks["tag_name"](tagName), nodes, recurse, limit);
  }
  function getElementsByClassName(className, nodes, recurse = true, limit = Number.POSITIVE_INFINITY) {
    return filter(getAttribCheck("class", className), nodes, recurse, limit);
  }
  function getElementsByTagType(type, nodes, recurse = true, limit = Number.POSITIVE_INFINITY) {
    return filter(Checks["tag_type"](type), nodes, recurse, limit);
  }

  // ../unison-sources/node_modules/entities/dist/escape.js
  function getEscape(char) {
    return char === 34 ? "&quot;" : char === 38 ? "&amp;" : char === 39 ? "&apos;" : char === 60 ? "&lt;" : char === 62 ? "&gt;" : "&nbsp;";
  }
  var XML_BITSET_VALUE = 1342177476;
  var xmlEncodeRegex = /["&'<>\u0080-\uFFFF]/g;
  function isXmlEscapable(code) {
    return code >= 128 || code >= 32 && code < 64 && (XML_BITSET_VALUE >>> code & 1) === 1;
  }
  function encodeXML(input) {
    const { length } = input;
    let out;
    let last = 0;
    let index = 0;
    while (index < length) {
      const char = input.charCodeAt(index);
      if (!isXmlEscapable(char)) {
        const bound = Math.min(index + 32, length);
        let next = index + 1;
        while (next < bound && !isXmlEscapable(input.charCodeAt(next))) {
          next++;
        }
        if (next < bound) {
          index = next;
          continue;
        }
        if (next >= length)
          break;
        xmlEncodeRegex.lastIndex = next;
        if (!xmlEncodeRegex.test(input))
          break;
        index = xmlEncodeRegex.lastIndex - 1;
        continue;
      }
      if (out === void 0)
        out = input.substring(0, index);
      else if (last !== index)
        out += input.substring(last, index);
      if (char < 64) {
        out += getEscape(char);
        last = index += 1;
        continue;
      }
      const cp = input.codePointAt(index);
      out += `&#x${cp.toString(16)};`;
      if (cp !== char)
        index++;
      last = index += 1;
    }
    if (out === void 0)
      return input;
    if (last < length)
      out += input.substr(last);
    return out;
  }
  function escapeWithRegex(re, data) {
    re.lastIndex = 0;
    if (!re.test(data))
      return data;
    let out = "";
    let last = 0;
    do {
      const index = re.lastIndex - 1;
      if (last !== index)
        out += data.substring(last, index);
      const char = data.charCodeAt(index);
      out += getEscape(char);
      last = index + 1;
    } while (re.test(data));
    return out + data.substring(last);
  }
  var attributeEscapeRegex = /["&\u{A0}]/gu;
  function escapeAttribute(data) {
    return escapeWithRegex(attributeEscapeRegex, data);
  }
  var textEscapeRegex = /[&<>\u{A0}]/gu;
  function escapeText(data) {
    return escapeWithRegex(textEscapeRegex, data);
  }

  // ../unison-sources/node_modules/entities/dist/index.js
  var EntityLevel;
  (function(EntityLevel2) {
    EntityLevel2[EntityLevel2["XML"] = 0] = "XML";
    EntityLevel2[EntityLevel2["HTML"] = 1] = "HTML";
  })(EntityLevel || (EntityLevel = {}));
  var EncodingMode;
  (function(EncodingMode2) {
    EncodingMode2[EncodingMode2["UTF8"] = 0] = "UTF8";
    EncodingMode2[EncodingMode2["ASCII"] = 1] = "ASCII";
    EncodingMode2[EncodingMode2["Extensive"] = 2] = "Extensive";
    EncodingMode2[EncodingMode2["Attribute"] = 3] = "Attribute";
    EncodingMode2[EncodingMode2["Text"] = 4] = "Text";
  })(EncodingMode || (EncodingMode = {}));

  // ../unison-sources/node_modules/dom-serializer/dist/foreign-names.js
  var elementNames = new Map("altGlyph altGlyphDef altGlyphItem animateColor animateMotion animateTransform clipPath feBlend feColorMatrix feComponentTransfer feComposite feConvolveMatrix feDiffuseLighting feDisplacementMap feDistantLight feDropShadow feFlood feFuncA feFuncB feFuncG feFuncR feGaussianBlur feImage feMerge feMergeNode feMorphology feOffset fePointLight feSpecularLighting feSpotLight feTile feTurbulence foreignObject glyphRef linearGradient radialGradient textPath".split(" ").map((name) => [name.toLowerCase(), name]));
  var attributeNames = new Map("definitionURL attributeName attributeType baseFrequency baseProfile calcMode clipPathUnits diffuseConstant edgeMode filterUnits glyphRef gradientTransform gradientUnits kernelMatrix kernelUnitLength keyPoints keySplines keyTimes lengthAdjust limitingConeAngle markerHeight markerUnits markerWidth maskContentUnits maskUnits numOctaves pathLength patternContentUnits patternTransform patternUnits pointsAtX pointsAtY pointsAtZ preserveAlpha preserveAspectRatio primitiveUnits refX refY repeatCount repeatDur requiredExtensions requiredFeatures specularConstant specularExponent spreadMethod startOffset stdDeviation stitchTiles surfaceScale systemLanguage tableValues targetX targetY textLength viewBox viewTarget xChannelSelector yChannelSelector zoomAndPan".split(" ").map((name) => [name.toLowerCase(), name]));

  // ../unison-sources/node_modules/dom-serializer/dist/index.js
  var unencodedElements = new Set("style script xmp iframe noembed noframes plaintext noscript".split(" "));
  var voidElements2 = new Set("area base basefont br col command embed frame hr img input isindex keygen link meta param source track wbr".split(" "));
  var foreignElements = /* @__PURE__ */ new Set(["svg", "math"]);
  var foreignModeIntegrationPoints = new Set("mi mo mn ms mtext annotation-xml foreignObject desc title".split(" "));
  function render(node, options = {}) {
    const nodes = "length" in node ? node : [node];
    const xmlMode = options.xmlMode ?? false;
    let output = "";
    for (let index = 0; index < nodes.length; index++) {
      output += renderNode(nodes[index], options, xmlMode);
    }
    return output;
  }
  var dist_default = render;
  function renderChildren(children, options, xmlMode) {
    let output = "";
    for (let index = 0; index < children.length; index++) {
      output += renderNode(children[index], options, xmlMode);
    }
    return output;
  }
  function renderNode(node, options, xmlMode) {
    switch (node.type) {
      case Root: {
        return renderChildren(node.children, options, xmlMode);
      }
      case Directive: {
        return `<${node.data}>`;
      }
      case Comment: {
        return `<!--${node.data}-->`;
      }
      case CDATA: {
        return `<![CDATA[${node.children[0].data}]]>`;
      }
      case Script:
      case Style:
      case Tag: {
        return renderTag(node, options, xmlMode);
      }
      case Text: {
        const element = node;
        const data = element.data || "";
        if ((options.encodeEntities ?? options.decodeEntities) !== false && !(!xmlMode && element.parent && unencodedElements.has(element.parent.name))) {
          return xmlMode || options.encodeEntities !== "utf8" ? encodeXML(data) : escapeText(data);
        }
        return data;
      }
    }
  }
  function renderTag(element, options, xmlMode) {
    if (xmlMode === "foreign") {
      element.name = elementNames.get(element.name) ?? element.name;
      if (element.parent && foreignModeIntegrationPoints.has(element.parent.name)) {
        xmlMode = false;
      }
    }
    if (!xmlMode && foreignElements.has(element.name)) {
      xmlMode = "foreign";
    }
    const { name, children } = element;
    const isVoid = !xmlMode && voidElements2.has(name);
    let tag = `<${name}${formatAttributes(element.attribs, options, xmlMode)}`;
    if (children.length === 0 && (xmlMode ? options.selfClosingTags !== false : options.selfClosingTags && isVoid)) {
      tag += xmlMode ? "/>" : " />";
    } else {
      tag += ">";
      if (children.length > 0) {
        tag += renderChildren(children, options, xmlMode);
      }
      if (!isVoid) {
        tag += `</${name}>`;
      }
    }
    return tag;
  }
  function replaceQuotes(value) {
    return value.replaceAll('"', "&quot;");
  }
  function formatAttributes(attributes, options, xmlMode) {
    if (!attributes)
      return "";
    const encode = (options.encodeEntities ?? options.decodeEntities) === false ? replaceQuotes : xmlMode || options.encodeEntities !== "utf8" ? encodeXML : escapeAttribute;
    const isForeign = xmlMode === "foreign";
    const showEmpty = !!(options.emptyAttrs ?? xmlMode);
    let result = "";
    for (const key in attributes) {
      if (!Object.hasOwn(attributes, key))
        continue;
      const value = attributes[key];
      const k = isForeign ? attributeNames.get(key) ?? key : key;
      result += !showEmpty && (value == null || value === "") ? ` ${k}` : ` ${k}="${encode(value == null ? "" : String(value))}"`;
    }
    return result;
  }

  // ../unison-sources/node_modules/domutils/dist/stringify.js
  function getOuterHTML(node, options) {
    return dist_default(node, options);
  }
  function getInnerHTML(node, options) {
    return hasChildren(node) ? node.children.map((node2) => getOuterHTML(node2, options)).join("") : "";
  }
  function getText(node) {
    if (Array.isArray(node))
      return node.map(getText).join("");
    if (isTag2(node))
      return node.name === "br" ? "\n" : getText(node.children);
    if (isCDATA(node))
      return getText(node.children);
    if (isText(node))
      return node.data;
    return "";
  }
  function textContent(node) {
    if (Array.isArray(node))
      return node.map(textContent).join("");
    if (hasChildren(node) && !isComment(node)) {
      return textContent(node.children);
    }
    if (isText(node))
      return node.data;
    return "";
  }
  function innerText(node) {
    if (Array.isArray(node))
      return node.map(innerText).join("");
    if (hasChildren(node) && (node.type === ElementType.Tag || isCDATA(node))) {
      return innerText(node.children);
    }
    if (isText(node))
      return node.data;
    return "";
  }

  // ../unison-sources/node_modules/domutils/dist/feeds.js
  function getFeed(document) {
    const feedRoot = getOneElement(isValidFeed, document);
    return feedRoot ? feedRoot.name === "feed" ? getAtomFeed(feedRoot) : getRssFeed(feedRoot) : null;
  }
  function getAtomFeed(feedRoot) {
    const childs = feedRoot.children;
    const feed = {
      type: "atom",
      items: getElementsByTagName("entry", childs).map((item) => {
        const { children } = item;
        const entry = { media: getMediaElements(children) };
        addConditionally(entry, "id", "id", children);
        addConditionally(entry, "title", "title", children);
        const href2 = getOneElement("link", children)?.attribs["href"];
        if (href2) {
          entry.link = href2;
        }
        const description = fetch2("summary", children) || fetch2("content", children);
        if (description) {
          entry.description = description;
        }
        const pubDate = fetch2("updated", children);
        if (pubDate) {
          entry.pubDate = new Date(pubDate);
        }
        return entry;
      })
    };
    addConditionally(feed, "id", "id", childs);
    addConditionally(feed, "title", "title", childs);
    const href = getOneElement("link", childs)?.attribs["href"];
    if (href) {
      feed.link = href;
    }
    addConditionally(feed, "description", "subtitle", childs);
    const updated = fetch2("updated", childs);
    if (updated) {
      feed.updated = new Date(updated);
    }
    addConditionally(feed, "author", "email", childs, true);
    return feed;
  }
  function getRssFeed(feedRoot) {
    const childs = getOneElement("channel", feedRoot.children)?.children ?? [];
    const feed = {
      type: feedRoot.name.substr(0, 3),
      id: "",
      items: getElementsByTagName("item", feedRoot.children).map((item) => {
        const { children } = item;
        const entry = { media: getMediaElements(children) };
        addConditionally(entry, "id", "guid", children);
        addConditionally(entry, "title", "title", children);
        addConditionally(entry, "link", "link", children);
        addConditionally(entry, "description", "description", children);
        const pubDate = fetch2("pubDate", children) || fetch2("dc:date", children);
        if (pubDate)
          entry.pubDate = new Date(pubDate);
        return entry;
      })
    };
    addConditionally(feed, "title", "title", childs);
    addConditionally(feed, "link", "link", childs);
    addConditionally(feed, "description", "description", childs);
    const updated = fetch2("lastBuildDate", childs);
    if (updated) {
      feed.updated = new Date(updated);
    }
    addConditionally(feed, "author", "managingEditor", childs, true);
    return feed;
  }
  var MEDIA_KEYS_STRING = ["url", "type", "lang"];
  var MEDIA_KEYS_INT = [
    "fileSize",
    "bitrate",
    "framerate",
    "samplingrate",
    "channels",
    "duration",
    "height",
    "width"
  ];
  function getMediaElements(where) {
    return getElementsByTagName("media:content", where).map((element) => {
      const { attribs } = element;
      const media = {
        medium: attribs["medium"],
        isDefault: !!attribs["isDefault"]
      };
      for (const attrib of MEDIA_KEYS_STRING) {
        if (attribs[attrib]) {
          media[attrib] = attribs[attrib];
        }
      }
      for (const attrib of MEDIA_KEYS_INT) {
        if (attribs[attrib]) {
          media[attrib] = Number.parseInt(attribs[attrib], 10);
        }
      }
      if (attribs["expression"]) {
        media.expression = attribs["expression"];
      }
      return media;
    });
  }
  function getOneElement(tagName, node) {
    return getElementsByTagName(tagName, node, true, 1)[0];
  }
  function fetch2(tagName, where, recurse = false) {
    return textContent(getElementsByTagName(tagName, where, recurse, 1)).trim();
  }
  function addConditionally(object, property, tagName, where, recurse = false) {
    const value = fetch2(tagName, where, recurse);
    if (value)
      object[property] = value;
  }
  function isValidFeed(value) {
    return value === "rss" || value === "feed" || value === "rdf:RDF";
  }

  // ../unison-sources/node_modules/domutils/dist/helpers.js
  function removeSubsets(nodes) {
    let index = nodes.length;
    while (--index >= 0) {
      const node = nodes[index];
      if (index > 0 && nodes.lastIndexOf(node, index - 1) >= 0) {
        nodes.splice(index, 1);
        continue;
      }
      for (let ancestor = node.parent; ancestor; ancestor = ancestor.parent) {
        if (nodes.includes(ancestor)) {
          nodes.splice(index, 1);
          break;
        }
      }
    }
    return nodes;
  }
  var DocumentPosition;
  (function(DocumentPosition2) {
    DocumentPosition2[DocumentPosition2["DISCONNECTED"] = 1] = "DISCONNECTED";
    DocumentPosition2[DocumentPosition2["PRECEDING"] = 2] = "PRECEDING";
    DocumentPosition2[DocumentPosition2["FOLLOWING"] = 4] = "FOLLOWING";
    DocumentPosition2[DocumentPosition2["CONTAINS"] = 8] = "CONTAINS";
    DocumentPosition2[DocumentPosition2["CONTAINED_BY"] = 16] = "CONTAINED_BY";
  })(DocumentPosition || (DocumentPosition = {}));
  function compareDocumentPosition(nodeA, nodeB) {
    const aParents = [];
    const bParents = [];
    if (nodeA === nodeB) {
      return 0;
    }
    let current = hasChildren(nodeA) ? nodeA : nodeA.parent;
    while (current) {
      aParents.unshift(current);
      current = current.parent;
    }
    current = hasChildren(nodeB) ? nodeB : nodeB.parent;
    while (current) {
      bParents.unshift(current);
      current = current.parent;
    }
    const maxIndex = Math.min(aParents.length, bParents.length);
    let index = 0;
    while (index < maxIndex && aParents[index] === bParents[index]) {
      index++;
    }
    if (index === 0) {
      return DocumentPosition.DISCONNECTED;
    }
    const sharedParent = aParents[index - 1];
    const siblings = sharedParent.children;
    const aSibling = aParents[index];
    const bSibling = bParents[index];
    if (siblings.indexOf(aSibling) > siblings.indexOf(bSibling)) {
      if (sharedParent === nodeB) {
        return DocumentPosition.FOLLOWING | DocumentPosition.CONTAINED_BY;
      }
      return DocumentPosition.FOLLOWING;
    }
    if (sharedParent === nodeA) {
      return DocumentPosition.PRECEDING | DocumentPosition.CONTAINS;
    }
    return DocumentPosition.PRECEDING;
  }
  function uniqueSort(nodes) {
    nodes = nodes.filter((node, index, array) => !array.includes(node, index + 1));
    nodes.sort((a, b) => {
      const relative = compareDocumentPosition(a, b);
      if (relative & DocumentPosition.PRECEDING) {
        return -1;
      }
      if (relative & DocumentPosition.FOLLOWING) {
        return 1;
      }
      return 0;
    });
    return nodes;
  }

  // ../unison-sources/node_modules/domutils/dist/manipulation.js
  function removeElement(element) {
    if (element.prev)
      element.prev.next = element.next;
    if (element.next)
      element.next.prev = element.prev;
    if (element.parent) {
      const childs = element.parent.children;
      const childsIndex = childs.lastIndexOf(element);
      if (childsIndex !== -1) {
        childs.splice(childsIndex, 1);
      }
    }
    element.next = null;
    element.prev = null;
    element.parent = null;
  }
  function replaceElement(element, replacement) {
    replacement.prev = element.prev;
    if (replacement.prev) {
      replacement.prev.next = replacement;
    }
    replacement.next = element.next;
    if (replacement.next) {
      replacement.next.prev = replacement;
    }
    replacement.parent = element.parent;
    if (replacement.parent) {
      const { children } = replacement.parent;
      const elementIndex = children.lastIndexOf(element);
      if (elementIndex === -1) {
        return;
      }
      children[elementIndex] = replacement;
      element.parent = null;
    }
  }
  function appendChild(parent, child) {
    removeElement(child);
    child.next = null;
    child.parent = parent;
    if (parent.children.push(child) > 1) {
      const sibling = parent.children[parent.children.length - 2];
      sibling.next = child;
      child.prev = sibling;
    } else {
      child.prev = null;
    }
  }
  function append(element, next) {
    removeElement(next);
    const { parent } = element;
    const currentNext = element.next;
    next.next = currentNext;
    next.prev = element;
    element.next = next;
    next.parent = parent;
    if (currentNext) {
      currentNext.prev = next;
      if (parent) {
        const childs = parent.children;
        childs.splice(childs.lastIndexOf(currentNext), 0, next);
      }
    } else if (parent) {
      parent.children.push(next);
    }
  }
  function prependChild(parent, child) {
    removeElement(child);
    child.parent = parent;
    child.prev = null;
    if (parent.children.unshift(child) === 1) {
      child.next = null;
    } else {
      const sibling = parent.children[1];
      sibling.prev = child;
      child.next = sibling;
    }
  }
  function prepend(element, previous) {
    removeElement(previous);
    const { parent } = element;
    if (parent) {
      const childs = parent.children;
      childs.splice(childs.indexOf(element), 0, previous);
    }
    if (element.prev) {
      element.prev.next = previous;
    }
    previous.parent = parent;
    previous.prev = element.prev;
    previous.next = element;
    element.prev = previous;
  }

  // ../unison-sources/node_modules/domutils/dist/traversal.js
  function getChildren(element) {
    return hasChildren(element) ? element.children : [];
  }
  function getParent(element) {
    return element.parent || null;
  }
  function getSiblings(element) {
    const parent = getParent(element);
    if (parent != null)
      return getChildren(parent);
    const siblings = [element];
    let { prev, next } = element;
    while (prev != null) {
      siblings.unshift(prev);
      ({ prev } = prev);
    }
    while (next != null) {
      siblings.push(next);
      ({ next } = next);
    }
    return siblings;
  }
  function getAttributeValue(element, name) {
    const { attribs } = element;
    return attribs?.[name];
  }
  function hasAttrib(element, name) {
    const { attribs } = element;
    return attribs != null && Object.hasOwn(attribs, name) && attribs[name] != null;
  }
  function getName(element) {
    return element.name;
  }
  function nextElementSibling(element) {
    let { next } = element;
    while (next !== null && !isTag2(next))
      ({ next } = next);
    return next;
  }
  function prevElementSibling(element) {
    let { prev } = element;
    while (prev !== null && !isTag2(prev))
      ({ prev } = prev);
    return prev;
  }

  // ../unison-sources/node_modules/htmlparser2/dist/index.js
  function parseDocument(data, options) {
    const handler = new DomHandler(void 0, options);
    new Parser(handler, options).end(data);
    return handler.root;
  }

  // ../unison-sources/lib/core/url.ts
  var RE = /^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/;
  function parseUrl(url) {
    const m = RE.exec(url.trim());
    if (!m) return null;
    return { protocol: m[1].toLowerCase(), host: m[2].toLowerCase(), pathname: m[3] || "/", search: m[4] || "", hash: m[5] || "" };
  }
  function resolveUrl(base, ref) {
    if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(ref)) return ref;
    const b = parseUrl(base);
    if (!b) return ref;
    if (ref.startsWith("//")) return b.protocol + ref;
    if (ref.startsWith("/")) return `${b.protocol}//${b.host}${ref}`;
    if (ref.startsWith("?")) return `${b.protocol}//${b.host}${b.pathname}${ref}`;
    if (ref.startsWith("#")) return `${b.protocol}//${b.host}${b.pathname}${b.search}${ref}`;
    const dir = b.pathname.replace(/[^/]*$/, "");
    const segs = [];
    for (const s of (dir + ref).split("/")) {
      if (s === "..") segs.pop();
      else if (s !== ".") segs.push(s);
    }
    return `${b.protocol}//${b.host}${segs.join("/")}`;
  }

  // ../unison-sources/lib/core/html.ts
  var DEFAULT_DROP_TAGS = [
    "script",
    "style",
    "noscript",
    "template",
    "nav",
    "header",
    "footer",
    "aside",
    "form",
    "button",
    "sup",
    "table",
    "link",
    "meta",
    "iframe",
    "svg"
  ];
  var DEFAULT_DROP_CLASSES = ["noprint", "navbox", "advert", "ads", "ad", "share", "comments", "footnotes", "reference", "references"];
  var BLOCK = /* @__PURE__ */ new Set(["p", "div", "dd", "li", "blockquote", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article", "center"]);
  function classes(el) {
    return (el.attribs.class || "").split(/\s+/).filter(Boolean);
  }
  function matches(el, sel) {
    if (sel.tag && el.name !== sel.tag) return false;
    if (sel.id && el.attribs.id !== sel.id) return false;
    if (sel.class && !classes(el).includes(sel.class)) return false;
    return true;
  }
  function normalizeSpaces(s) {
    return s.replace(/[       　]/g, " ").replace(/[­​‌‍⁠﻿]/g, "").replace(/[ \t\r\n\f\v]+/g, " ").trim();
  }
  function normalizeRu(s) {
    s = s.replace(/^[-‐‑‒–―]\s+/, "— ");
    s = s.replace(/ [-–] /g, " — ");
    s = s.replace(/"([^"]*)"/g, "«$1»");
    return s;
  }
  function normalizeEn(s) {
    s = s.replace(/(^|[\s(\[{—–-])"/g, "$1“").replace(/"/g, "”");
    s = s.replace(/(^|[\s(\[{—–-])'/g, "$1‘").replace(/'/g, "’");
    return s;
  }
  function normalizeText(s, lang) {
    const t = normalizeSpaces(s);
    return lang === "en" ? normalizeEn(t) : normalizeRu(t);
  }
  var TERMINAL = /[.!?…:;»"”’)\]——]$/;
  var LOWER_START = /^[a-zа-яё]/;
  function parseChapterHtml(html, opts) {
    const doc = parseDocument(html, { decodeEntities: true });
    let rootNodes = doc.children;
    if (opts.root) {
      const r = dist_exports2.findOne((e) => matches(e, opts.root), doc.children, true);
      if (r) rootNodes = r.children;
    }
    const dropTags = /* @__PURE__ */ new Set([...DEFAULT_DROP_TAGS, ...opts.dropTags || []]);
    const dropClasses = /* @__PURE__ */ new Set([...DEFAULT_DROP_CLASSES, ...opts.dropClasses || []]);
    const dropIds = new Set(opts.dropIds || []);
    const minW = opts.minImageWidth ?? 100;
    const titleFromHeading = opts.titleFromHeading !== false;
    const paras = [];
    const images = [];
    let title = null;
    let buf = "";
    let stopped = false;
    const flush = () => {
      const t = normalizeText(buf, opts.lang);
      buf = "";
      if (t) paras.push(t);
    };
    const shouldDrop = (n) => {
      if (n.type === "comment" || n.type === "directive") return true;
      if (n.type !== "tag" && n.type !== "script" && n.type !== "style") return false;
      const el = n;
      if (dropTags.has(el.name)) return true;
      if (classes(el).some((c) => dropClasses.has(c))) return true;
      if (el.attribs.id && dropIds.has(el.attribs.id)) return true;
      if (/display\s*:\s*none/i.test(el.attribs.style || "")) return true;
      if (el.attribs.hidden !== void 0 || el.attribs["aria-hidden"] === "true") return true;
      return false;
    };
    const walk = (nodes) => {
      for (const n of nodes) {
        if (stopped) return;
        if (shouldDrop(n)) continue;
        if (n.type === "text") {
          buf += n.data;
          continue;
        }
        if (n.type !== "tag") continue;
        const el = n;
        if (el.name === "br") {
          buf += " ";
          continue;
        }
        if (el.name === "img") {
          const src = el.attribs.src || el.attribs["data-src"];
          const w = parseInt(el.attribs.width || "", 10) || void 0;
          const h = parseInt(el.attribs.height || "", 10) || void 0;
          if (src && (w === void 0 || w >= minW)) {
            flush();
            const img = { after: paras.length - 1, src: opts.baseUrl ? resolveUrl(opts.baseUrl, src) : src };
            if (w) img.w = w;
            if (h) img.h = h;
            images.push(img);
          }
          continue;
        }
        if (/^h[1-6]$/.test(el.name)) {
          const t = normalizeSpaces(dist_exports2.textContent(el));
          if (opts.stopHeading && opts.stopHeading.test(t)) {
            flush();
            stopped = true;
            return;
          }
          if (titleFromHeading && title === null && paras.length === 0 && t) {
            flush();
            title = t;
            continue;
          }
        }
        const block = BLOCK.has(el.name);
        if (block) flush();
        walk(el.children);
        if (block) flush();
      }
    };
    walk(rootNodes);
    flush();
    const glued = [];
    const remap = [];
    for (let i = 0; i < paras.length; i++) {
      const p = paras[i];
      const prev = glued[glued.length - 1];
      if (prev !== void 0 && !TERMINAL.test(prev) && LOWER_START.test(p)) glued[glued.length - 1] = prev + " " + p;
      else glued.push(p);
      remap[i] = glued.length - 1;
    }
    for (const img of images) img.after = img.after < 0 ? -1 : remap[img.after];
    return { title: title ?? (opts.fallbackTitle || ""), paras: glued, images };
  }

  // ../unison-sources/lib/core/prosemirror.ts
  var BLOCK_TAG = {
    paragraph: "p",
    blockquote: "blockquote",
    heading: "h3",
    listItem: "li",
    bulletList: "ul",
    orderedList: "ol",
    codeBlock: "pre"
  };
  var MARK_TAG = { bold: "b", italic: "i", underline: "u", strike: "s", code: "code" };
  function escapeHtml(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  function isPmDoc(v) {
    return !!v && typeof v === "object" && v.type === "doc" && Array.isArray(v.content);
  }
  function prosemirrorToHtml(doc, image) {
    const out = [];
    const walk = (nodes) => {
      for (const n of nodes || []) {
        if (!n || typeof n !== "object") continue;
        switch (n.type) {
          case "text": {
            let t = escapeHtml(typeof n.text === "string" ? n.text : "");
            for (const m of n.marks || []) {
              const tag = MARK_TAG[m.type];
              if (tag) t = `<${tag}>${t}</${tag}>`;
            }
            out.push(t);
            break;
          }
          case "hardBreak":
            out.push("<br>");
            break;
          case "horizontalRule":
            out.push("<hr>");
            break;
          case "image": {
            for (const im of (image ? image(n) : null) || []) {
              out.push(`<img src="${escapeHtml(im.src)}"${im.w ? ` width="${im.w}"` : ""}${im.h ? ` height="${im.h}"` : ""}>`);
            }
            break;
          }
          default: {
            const tag = BLOCK_TAG[n.type] || (n.content ? "div" : "");
            if (!tag) break;
            out.push(`<${tag}>`);
            walk(n.content);
            out.push(`</${tag}>`);
          }
        }
      }
    };
    walk(doc.content);
    return out.join("");
  }

  // plugins/ranobelib/index.ts
  var API = "https://api.cdnlibs.org/api";
  var SITE = "https://ranobelib.me";
  var HEADERS = { Accept: "application/json", "Site-Id": "3", Referer: SITE + "/" };
  var ADULT_AGE = 4;
  function qs(params) {
    return params.map(([k, v]) => k + "=" + encodeURIComponent(String(v))).join("&");
  }
  async function api(path) {
    const url = API + path;
    const r = await fetch(url, { headers: HEADERS });
    if (r.status === 404) return null;
    if (r.status === 429) fail("RATE_LIMITED", "RanobeLib API 429");
    if (r.status === 401) fail("NEEDS_LOGIN", "Войдите в RanobeLib снова");
    if (r.status === 403 && !/json/.test(r.headers["content-type"] || "")) fail("NEEDS_BROWSER", "Проверка защиты сайта", SITE + "/ru");
    if (r.status >= 500) fail("UNAVAILABLE", `RanobeLib API ${r.status}`, url);
    if (r.status !== 200) fail("CHANGED_LAYOUT", `HTTP ${r.status}`, url);
    try {
      return JSON.parse(r.text);
    } catch {
      fail("CHANGED_LAYOUT", "not JSON", url);
    }
  }
  var titleOf = (b) => normalizeSpaces(b.rus_name || b.eng_name || b.name || String(b.id));
  var isAdult = (b) => (b.ageRestriction?.id ?? 0) >= ADULT_AGE;
  function stub(b) {
    const s = { id: String(b.slug_url || b.id + "--" + b.slug), title: titleOf(b) };
    if (b.cover?.default) s.cover = b.cover.default;
    if (isAdult(b)) s.nsfw = true;
    return s;
  }
  async function list(params, page) {
    const d = await api("/manga?" + qs([["site_id[]", 3], ...params, ["page", page]]));
    if (!d || !Array.isArray(d.data)) fail("CHANGED_LAYOUT", "no book list");
    return { items: d.data.map(stub), hasNext: !!d.meta?.has_next_page };
  }
  function filterParams(f, defaultSort) {
    const out = [];
    const sort = typeof f?.sort === "string" && f.sort ? f.sort : defaultSort;
    out.push(["sort_by", sort.replace(/^-/, "")], ["sort_type", sort.startsWith("-") ? "desc" : "asc"]);
    for (const [id, key] of [["types", "types[]"], ["genres", "genres[]"], ["status", "manga_status[]"], ["translation", "scanlateStatus[]"]]) {
      const v = f?.[id];
      for (const x of Array.isArray(v) ? v : typeof v === "string" && v ? [v] : []) out.push([key, x]);
    }
    if (f?.withChapters !== false) out.push(["chapters[min]", 1]);
    return out;
  }
  function summaryText(s) {
    const html = typeof s === "string" ? s : isPmDoc(s) ? prosemirrorToHtml(s) : "";
    if (!html) return void 0;
    const { paras } = parseChapterHtml(html, { lang: "ru", titleFromHeading: false });
    return paras.length ? paras.join("\n\n") : void 0;
  }
  function status(d) {
    const tr = d.scanlateStatus?.id, ti = d.status?.id;
    if (tr === 1) return "ongoing";
    if (tr === 2 && ti === 2) return "completed";
    if (ti === 1) return "ongoing";
    return "unknown";
  }
  var iso = (s) => typeof s === "string" && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d/.test(s) ? s.slice(0, 19) + "Z" : void 0;
  var bookUrl = (id) => SITE + "/ru/book/" + id;
  var chapterUrl = (id, v, n, b) => SITE + "/ru/" + id + "/read/v" + encodeURIComponent(v) + "/c" + encodeURIComponent(n) + (b ? "?bid=" + b : "");
  function parseChapterId(chapterId) {
    const m = /^([^/@]+)\/([^/@]+)(?:@(\d+))?$/.exec(chapterId);
    if (!m) fail("NOT_FOUND", "bad chapter id " + chapterId);
    return { volume: m[1], number: m[2], branch: m[3] || null };
  }
  var chapterTitle = (number, name) => "Глава " + number + (typeof name === "string" && normalizeSpaces(name) ? ". " + normalizeSpaces(name) : "");
  var branchKey = (b) => b === null || b === void 0 ? "" : String(b);
  var plugin = {
    popular: (page, filters) => list(filterParams(filters, "-rating_score"), page),
    latest: (page) => list([["sort_by", "last_chapter_at"], ["sort_type", "desc"], ["chapters[min]", 1]], page),
    search: (query2, page, filters) => list([["q", query2], ...filterParams(filters, "-rating_score")], page),
    async filters() {
      const d = await api("/constants?" + qs([["fields[]", "genres"], ["fields[]", "types"], ["fields[]", "status"], ["fields[]", "scanlateStatus"]]));
      if (!d?.data) fail("CHANGED_LAYOUT", "no constants");
      const forSite = (xs) => (xs || []).filter((x) => (x.site_ids || []).includes(3));
      const opts = (xs) => forSite(xs).map((x) => ({ id: String(x.id), title: String(x.label || x.name) }));
      return [
        { id: "sort", title: "Сортировка", type: "sort", default: "-rating_score", options: [
          { id: "rating_score", title: "По популярности" },
          { id: "rate_avg", title: "По рейтингу" },
          { id: "views", title: "По просмотрам" },
          { id: "chap_count", title: "По количеству глав" },
          { id: "last_chapter_at", title: "По дате обновления" },
          { id: "created_at", title: "По дате добавления" },
          { id: "rus_name", title: "По названию" }
        ] },
        { id: "types", title: "Тип", type: "multiselect", options: opts(d.data.types) },
        { id: "genres", title: "Жанры", type: "multiselect", options: opts(d.data.genres) },
        { id: "status", title: "Статус тайтла", type: "multiselect", options: opts(d.data.status) },
        { id: "translation", title: "Статус перевода", type: "multiselect", options: opts(d.data.scanlateStatus) },
        { id: "withChapters", title: "Только с главами", type: "toggle", default: true }
      ];
    },
    async details(bookId) {
      const fields = ["summary", "genres", "tags", "teams", "authors", "status_id"];
      const d = (await api("/manga/" + encodeURIComponent(bookId) + "?" + qs(fields.map((f) => ["fields[]", f]))))?.data;
      if (!d) fail("NOT_FOUND", "no such book", bookUrl(bookId));
      const out = {
        id: bookId,
        title: titleOf(d),
        tags: [...new Set([...d.genres || [], ...d.tags || []].map((t) => String(t.name)).filter(Boolean))],
        status: status(d),
        lang: "ru",
        url: bookUrl(bookId)
      };
      const authors = (d.authors || []).map((a) => a.name).filter(Boolean);
      if (authors.length) out.author = authors.join(", ");
      const teams = (d.teams || []).map((t) => t.name).filter(Boolean);
      if (teams.length) out.translator = teams.join(", ");
      if (d.cover?.default) out.cover = d.cover.default;
      const desc = summaryText(d.summary);
      if (desc) out.description = desc;
      if (isAdult(d)) out.nsfw = true;
      return out;
    },
    async chapters(bookId) {
      const d = await api("/manga/" + encodeURIComponent(bookId) + "/chapters");
      if (!d) fail("NOT_FOUND", "no such book", bookUrl(bookId));
      if (!Array.isArray(d.data)) fail("CHANGED_LAYOUT", "no chapter list");
      const chs = d.data.filter((c) => Array.isArray(c.branches) && c.branches.length);
      const key = "branch:" + bookId;
      let main = await storage.get(key);
      if (main === null || !chs.some((c) => c.branches.some((b) => branchKey(b.branch_id) === main))) {
        const count = /* @__PURE__ */ new Map();
        for (const c of chs) for (const b of c.branches) count.set(branchKey(b.branch_id), (count.get(branchKey(b.branch_id)) || 0) + 1);
        main = [...count.entries()].sort((a, b) => b[1] - a[1] || (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))[0]?.[0] ?? "";
        await storage.set(key, main);
      }
      const volumes = new Set(chs.map((c) => String(c.volume)));
      return chs.map((c) => {
        const b = c.branches.find((x) => branchKey(x.branch_id) === main) || c.branches[0];
        const v = String(c.volume), n = String(c.number), bid = branchKey(b.branch_id) || null;
        const s = { id: v + "/" + n + (bid ? "@" + bid : ""), title: chapterTitle(n, c.name), url: chapterUrl(bookId, v, n, bid) };
        if (volumes.size > 1) s.volume = "Том " + v;
        const date = iso(b.created_at);
        if (date) s.date = date;
        return s;
      });
    },
    async chapter(bookId, chapterId) {
      const { volume, number, branch } = parseChapterId(chapterId);
      const params = [["number", number], ["volume", volume]];
      if (branch) params.unshift(["branch_id", branch]);
      const url = chapterUrl(bookId, volume, number, branch);
      const res = await api("/manga/" + encodeURIComponent(bookId) + "/chapter?" + qs(params));
      if (!res) {
        const book = (await api("/manga/" + encodeURIComponent(bookId)))?.data;
        if (book && isAdult(book)) fail("NEEDS_LOGIN", "Глава 18+: войдите в RanobeLib", url);
        fail("NOT_FOUND", "no such chapter", url);
      }
      const d = res.data;
      const atts = Array.isArray(d.attachments) ? d.attachments : [];
      const abs = (u) => /^https?:\/\//.test(u) ? u : SITE + (u.startsWith("/") ? "" : "/") + u;
      let html;
      if (isPmDoc(d.content)) {
        html = prosemirrorToHtml(d.content, (n) => {
          const refs = (n.attrs?.images || []).map((x) => String(x.image));
          const found = refs.map((r) => atts.find((a) => String(a.name) === r || String(a.id) === r)).filter(Boolean);
          if (!found.length && typeof n.attrs?.src === "string") return [{ src: abs(n.attrs.src) }];
          return found.map((a) => ({ src: abs(String(a.url)), w: a.width || void 0, h: a.height || void 0 }));
        });
      } else if (typeof d.content === "string") html = d.content;
      else fail("CHANGED_LAYOUT", "unknown chapter content", url);
      const title = chapterTitle(String(d.number ?? number), d.name);
      const parsed = parseChapterHtml(html, { lang: "ru", titleFromHeading: false, fallbackTitle: title, baseUrl: SITE + "/" });
      return { kind: "text", title, paras: parsed.paras, images: parsed.images };
    },
    async bookIdFromUrl(url) {
      const u = parseUrl(url);
      if (!u || !/(^|\.)ranobelib\.me$/.test(u.host)) return null;
      const m = /^\/(?:[a-z]{2}\/)?(?:book\/)?(\d+--[^/?#]+)/.exec(u.pathname);
      return m ? decodeURIComponent(m[1]) : null;
    }
  };
  definePlugin(manifest_default, plugin);
})();
