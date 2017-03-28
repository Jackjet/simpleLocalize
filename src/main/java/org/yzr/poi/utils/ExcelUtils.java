package org.yzr.poi.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import freemarker.template.Configuration;
import freemarker.template.Version;
import freemarker.template.Template;
import org.apache.poi.ss.usermodel.*;
import org.yzr.poi.model.CopyWriteContainer;
import org.yzr.poi.model.Localize;

/**
 * Created by yizhaorong on 2017/3/28.
 */
public class ExcelUtils {

    // 读取Excel
    public static List<CopyWriteContainer> read(String filePath) throws Exception {
        List<CopyWriteContainer> copyWriteContainers = new ArrayList<>();

        if(filePath == null || filePath.length() < 1) {
            return copyWriteContainers;
        }

        File file = new File(PropertiesManager.getProperty("basePath"));
        if(file.exists()) {
            FileUtils.deleteDir(file);
        }

        Workbook wb = WorkbookFactory.create(new File(filePath));
        for (Sheet sheet : wb ) {
            List<Localize> list = new ArrayList<>();
            boolean started = false;
            Integer keyColumn = 0;
            for (Row row : sheet) {
                Localize localize = new Localize();
                for (Cell cell : row) {
                    if(!started && cell.getCellType() == Cell.CELL_TYPE_STRING) {
                        if (Constant.START_KEY.equalsIgnoreCase(cell.getStringCellValue())) {
                            started = true;
                            keyColumn = cell.getColumnIndex();
                        } else {
                            continue;
                        }
                    }
                    switch (cell.getCellType()) {
                        case Cell.CELL_TYPE_BLANK:
                            break;
                        case Cell.CELL_TYPE_BOOLEAN:
                            break;
                        case Cell.CELL_TYPE_ERROR:
                            break;
                        case Cell.CELL_TYPE_FORMULA:
                            break;
                        case Cell.CELL_TYPE_NUMERIC:
                            break;
                        case Cell.CELL_TYPE_STRING:
                            if (Constant.END_KEY.equalsIgnoreCase(cell.getStringCellValue())) {
                                started = false;
                                continue;
                            }
                            if (keyColumn == cell.getColumnIndex()) {
                                localize.setKey(cell.getStringCellValue());
                            } else {
                                localize.putValue(cell.getStringCellValue());
                            }
                            break;
                        default:
                            break;
                    }
                    // Do something here
                }
                if(localize.getKey() != null) {
                    list.add(localize);
                }

            }

            if (list.size() < 1) {
                break;
            }

            Localize localize = list.get(0);
            int languageCount = localize.getValues().size();

            if (languageCount < 1) {
                break;
            }

            if (!localize.getKey().equalsIgnoreCase(Constant.START_KEY)) {
                break;
            }

            for (int i = 0; i < languageCount; i++) {
                String languageKey = localize.getValues().get(i);
                // 是文案 Key
                if (isLanguageKey(languageKey)) {
                    CopyWriteContainer copyWriteContainer = new CopyWriteContainer();
                    copyWriteContainer.setLanguage(languageKey);

                    for(int l = 1; l < list.size(); l++) {
                        Localize currentLocalize = list.get(l);

                        Localize dataLocalize = new Localize();
                        Localize androidDataLocalize = new Localize();

                        String key = currentLocalize.getKey().trim();
                        int currentLocalizeValueCount = currentLocalize.getValues().size();
                        if (currentLocalizeValueCount <= i) {
                            if (!key.toLowerCase().equals(Constant.COMMENT_KEY)) {
                                copyWriteContainer.getLostCopyWrites().add(key);
                            }
                            continue;
                        }

                        String localValue = currentLocalize.getValues().get(i).trim();
                        localValue = localValue.replaceAll("\"", "\\\\\"");
                        dataLocalize.setKey(key);
                        dataLocalize.putValue(localValue);
                        copyWriteContainer.getCopyWrites().add(dataLocalize);

                        String androidValue = localValue;
                        androidValue = androidValue.replaceAll("&", "&amp;");
                        androidValue = androidValue.replaceAll("<", "&lt;");
                        androidValue = androidValue.replaceAll("'", "\\\\'");
                        androidDataLocalize.setKey(key);
                        androidDataLocalize.putValue(androidValue);
                        copyWriteContainer.getAndroidCopyWrites().add(androidDataLocalize);
                    }
                    copyWriteContainers.add(copyWriteContainer);
                }
            }

            return copyWriteContainers;
        }

        return copyWriteContainers;
    }


    public static void generate(CopyWriteContainer copyWriteContainer) throws Exception  {
        String language = copyWriteContainer.getLanguage();
        if (language.equals("cn")) {
            Boolean ignoreChinese = Boolean.valueOf(PropertiesManager.getProperty(Constant.IGNORE_CHINESE));
            if (ignoreChinese) return;
        }

        Boolean useDefaultValue = new Boolean(PropertiesManager.getProperty(Constant.USE_DEFAULT_VALUE));
        if (useDefaultValue) {
            String defaultValue = PropertiesManager.getProperty(Constant.DEFAULT_VALUE);
            for (String key : copyWriteContainer.getLostCopyWrites()) {
                Localize localize = new Localize();
                localize.setKey(key);
                localize.putValue(defaultValue);
                copyWriteContainer.getCopyWrites().add(localize);
                copyWriteContainer.getAndroidCopyWrites().add(localize);
            }
        }

        Boolean iOSIsOpen = new Boolean(PropertiesManager.getProperty(Constant.IOS_SWITCH));
        if (iOSIsOpen) {
            createLocalizeFile(language, Constant.IOS_KEY, copyWriteContainer.getCopyWrites());
        }

        Boolean serverIsOpen = new Boolean(PropertiesManager.getProperty(Constant.SERVER_SWITCH));
        if (serverIsOpen) {
            createLocalizeFile(language, Constant.SERVER_KEY, copyWriteContainer.getCopyWrites());
        }

        Boolean androidIsOpen = new Boolean(PropertiesManager.getProperty(Constant.ANDROID_SWITCH));
        if (androidIsOpen) {
            createLocalizeFile(language, Constant.ANDROID_KEY, copyWriteContainer.getAndroidCopyWrites());
        }
    }

    /***
     * 生成本地化文件
     * @param code
     * @param language
     * @param localizes
     * @return
     * @throws Exception
     */
    private static void createLocalizeFile(String language, String code, List<Localize> localizes) throws Exception {
        Writer out = null;
        try {
            out = new StringWriter();
            String filePath = null;
            String basePath = PropertiesManager.getProperty("basePath") + File.separator + PropertiesManager.getProperty(code) + File.separator;
            if(code.equalsIgnoreCase(Constant.IOS_KEY)) {
                filePath = basePath +language+ ".lproj" + File.separator + PropertiesManager.getProperty(code+"FileName");
            } else if(code.equalsIgnoreCase(Constant.ANDROID_KEY)) {
                if (language.equals("en")) {
                    Boolean ignoreEnglishSuffix = Boolean.valueOf(PropertiesManager.getProperty(Constant.IGNORE_ENGLISH_SUFFIX));
                    if (ignoreEnglishSuffix) {
                        language = "";
                    } else {
                        language = "-en";
                    }
                    filePath = basePath +"values" + language + File.separator + PropertiesManager.getProperty(code+"FileName");
                } else {
                    if (language.equals("id")) {
                        language = "in";
                    }
                    filePath = basePath +"values-" + language + File.separator + PropertiesManager.getProperty(code+"FileName");
                }

            } else if (code.equalsIgnoreCase(Constant.SERVER_KEY)) {
                filePath = basePath + PropertiesManager.getProperty(code+"FileName") + language + ".properties";
            }
            File file = new File(filePath);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            Configuration cfg = new Configuration(new Version(2, 3, 21));
            cfg.setDefaultEncoding("UTF-8");
            cfg.setClassForTemplateLoading(ExcelUtils.class, "/templete");
            Template template = cfg.getTemplate(code + ".ftl");
            // 静态页面要存放的路径
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), "UTF-8"));
            // 处理模版 map数据 ,输出流
            Map<String, List<Localize>> dataModel = new HashMap<>();
            dataModel.put("list", localizes);
            template.process(dataModel, out);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }


    /***
     * 是否是语言Key
     * @param key
     * @return true or false
     */
    private static boolean isLanguageKey(String key) {
        Pattern pattern = Pattern.compile(Constant.LANGUAGE_EX);
        Matcher matcher = pattern.matcher(key);
        return matcher.matches();
    }
}
