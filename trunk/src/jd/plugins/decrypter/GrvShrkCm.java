//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision: 12797 $", interfaceVersion = 2, names = { "grooveshark.com" }, urls = { "http://listen\\.grooveshark\\.com/(#/)?.+" }, flags = { 0 })
public class GrvShrkCm extends PluginForDecrypt {

    private static final String LISTEN = "http://listen.grooveshark.com/";
    private static final String USERID = UUID.randomUUID().toString().toUpperCase();

    public GrvShrkCm(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        if (parameter.contains("search/song") || (parameter.endsWith("similar") && parameter.contains("artist"))) {
            this.logger.warning("Link format is not supported: " + parameter);
            return null;
        }
        // single
        if (parameter.contains("/s/")) {
            parameter = parameter.replaceFirst("grooveshark\\.com", "grooveshark.viajd");
            parameter = parameter.replace("#/", "");
            final DownloadLink dlLink = this.createDownloadlink(Encoding.htmlDecode(parameter));
            dlLink.setProperty("type", "single");
            decryptedLinks.add(dlLink);
            return decryptedLinks;
        }
        // album
        this.br.getPage(parameter);
        final String country = this.br.getRegex(Pattern.compile("\"country(.*?)}", Pattern.UNICODE_CASE)).getMatch(-1);
        final HashMap<String, String> titleContent = new HashMap<String, String>();
        if (parameter.contains("artist") || parameter.contains("album") || parameter.contains("popular")) {
            final String ID = parameter.substring(parameter.lastIndexOf("/") + 1);
            String method = new Regex(parameter, "#/(.*?)/").getMatch(0);
            if (method == null) {
                method = "popular";
            }
            String rawPost = this.getRequestToken(parameter, method + "GetSongs", country);
            Boolean type = true;
            String t1 = null;
            for (int i = 1; i <= 2; i++) {
                String rawPostTrue;
                if (method.equals("artist") || method.equals("album")) {
                    rawPostTrue = rawPost + "\"parameters\":{\"" + method + "ID\":" + ID + ",\"isVerified\":" + type.toString() + ",\"offset\":0}}";
                } else {
                    rawPostTrue = rawPost + "\"parameters\":{\"type\":\"daily\"}}";
                    i = 2;
                }
                this.br.getHeaders().put("Content-Type", "application/json");
                this.br.postPageRaw(GrvShrkCm.LISTEN + "more.php?" + method + "GetSongs", rawPostTrue);
                if (!type) {
                    if (t1.length() != 0) {
                        t1 = t1.concat(",");
                    }
                    t1 = t1 + this.br.getRegex("songs\":\\[(.*?)\\],\"hasMore").getMatch(0);
                    i = 2;
                } else {
                    t1 = this.br.getRegex("songs\":\\[(.*?)\\](,\"hasMore|}})").getMatch(0);
                    type = false;
                }
            }
            t1 = this.U2U(t1.replace("#", "-").replace("},{", "}#{"));
            final String[] t2 = t1.split("#");
            progress.setRange(t2.length);
            for (final String t3 : t2) {
                final String[] line = t3.replace("null", "\"null\"").replace("\",\"", "\"#\"").replaceAll("\\{|\\}", "").split("#");
                for (final String t4 : line) {
                    final String[] finalline = t4.replace("\":\"", "\"#\"").replace("\"", "").split("#");
                    if (finalline.length < 2) {
                        continue;
                    }
                    titleContent.put(finalline[0], finalline[1]);
                }
                // getTokenForSong
                rawPost = this.getRequestToken(parameter, "getTokenForSong", country);
                rawPost = rawPost + "\"parameters\":{\"songID\":\"" + titleContent.get("SongID") + "\"," + country + "}}";
                this.br.getHeaders().put("Content-Type", "application/json");
                this.br.postPageRaw(GrvShrkCm.LISTEN + "more.php?getTokenForSong", rawPost);

                final String Name = titleContent.get("Name").replace(" ", "_").replaceAll("\\?", "");
                final String Token = this.br.getRegex("Token\":\"(\\w+)\"").getMatch(0);
                final String ArtistName = titleContent.get("ArtistName");
                final String AlbumName = titleContent.get("AlbumName");
                String fpName = ArtistName + "_" + AlbumName;
                if (method == "popular") {
                    fpName = "popular_Top_Ten";
                }

                String dllink = GrvShrkCm.LISTEN + "s/" + Name + "/" + Token;
                dllink = dllink.replaceFirst("grooveshark\\.com", "grooveshark.viajd");
                final DownloadLink dl = this.createDownloadlink(dllink.trim());

                final FilePackage fp = FilePackage.getInstance();
                fp.setName(fpName.trim());
                dl.setFilePackage(fp);
                decryptedLinks.add(dl);
                progress.increase(1);
                if ((method == "popular") && (decryptedLinks.size() > 9)) {
                    break;
                }
            }
        }
        if (decryptedLinks.size() == 0) {
            this.logger.warning("Decrypter out of date for link: " + parameter);
            return null;
        }
        return decryptedLinks;
    }

    private String getRequestToken(final String parameter, final String method, final String country) throws IOException {
        this.br.getHeaders().put("Content-Type", "application/json");
        this.br.getHeaders().put("Referer", parameter);
        final String sid = this.br.getCookie(parameter, "PHPSESSID");
        final String token = this.getSecretToken(method, JDHash.getMD5(sid), sid);
        final String str = "{\"header\":{\"client\":\"htmlshark\",\"clientRevision\":20100831," + country + ",\"uuid\":\"" + GrvShrkCm.USERID + "\",\"session\":\"" + sid + "\",\"token\":\"" + token + "\"},\"method\":\"" + method + "\",";
        return str;
    }

    private String getSecretToken(final String method, final String token, final String sid) throws IOException {
        this.br.getHeaders().put("Content-Type", "application/json");
        String secretKey = "{\"parameters\":{\"secretKey\":\"" + token + "\"},\"header\":{\"client\":\"jsqueue\",\"clientRevision\":\"20100831.08\",\"session\":\"" + sid + "\",\"uuid\":\"" + GrvShrkCm.USERID + "\"},\"method\":\"getCommunicationToken\"}";
        this.br.postPageRaw("https://cowbell.grooveshark.com/service.php", secretKey);
        secretKey = this.br.getRegex("result\":\"(.*?)\"").getMatch(0);
        final String lastRandomizer = this.makeNewRandomizer();
        final String z = lastRandomizer + JDHash.getSHA1(method + ":" + secretKey + ":quitStealinMahShit:" + lastRandomizer);
        return z;
    }

    private String makeNewRandomizer() {
        final String charList = "0123456789abcdef";
        final char[] chArray = new char[6];
        final Random random = new Random();
        int i = 0;
        do {
            chArray[i] = charList.toCharArray()[random.nextInt(16)];
            i++;
        } while (i <= 5);
        return new String(chArray);
    }

    private String U2U(final String s) {
        final Pattern p = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        String res = s;
        final Matcher m = p.matcher(res);
        while (m.find()) {
            res = res.replaceAll("\\" + m.group(0), Character.toString((char) Integer.parseInt(m.group(1), 16)));
        }
        return res;
    }
}