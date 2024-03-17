#!/usr/bin/env kotlin

/**
 * リソース自動生成スクリプト
 *
 * @author MORIMORI0317
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
@file:DependsOn("com.google.code.gson:gson:2.10.1")
@file:DependsOn("com.google.guava:guava:33.0.0-jre")
@file:DependsOn("org.apache.commons:commons-lang3:3.14.0")
@file:DependsOn("commons-io:commons-io:2.15.1")
@file:DependsOn("dev.felnull:felnull-java-library:1.75")

import com.google.common.base.Stopwatch
import com.google.common.base.Suppliers
import com.google.common.collect.Comparators
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.felnull.fnjl.util.FNDataUtil
import dev.felnull.fnjl.util.FNStringUtil
import kotlinx.coroutines.*
import org.apache.commons.io.FileUtils
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Comparator
import java.util.HashMap
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.streams.toList // <- 消したらActions本番環境で動かなくなる

/*
それぞれのディレクトリの名称
パック(pack) -> ./pack (実際は./out/TexturePackにコピーされた後の階層)
リソース(resource) -> ./generate/resources
テンプレート(templates) -> ./generate/templates
*/

/**
 * Json処理用GSOオォン！アォン！
 */
val gson = Gson()

/**
 * ShortLifeのID
 */
val slId = "shortlife"

/**
 * MinecraftのID
 */
val mcId = "minecraft"

/**
 * MinecraftクライアントJarのダウンロードリンク (1.20.4)
 */
val mcJarLink = "https://piston-data.mojang.com/v1/objects/fd19469fed4a4b4c15b2d5133985f0e3e7816a8a/client.jar"

/**
 * 処理用CoroutineScope
 */
val scope = CoroutineScope(EmptyCoroutineContext)

/**
 * mkdir用ロック
 */
val mkdirLock = Object()

/**
 * 処理時間計測用ストップウォッチ
 */
val stopWatch = Stopwatch.createStarted()

/**
 * Httpクライアント
 */
val httpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(Duration.of(3, ChronoUnit.SECONDS))
    .build()

/* --- フォルダ/ファイル --- */

/**
 * オリジナルのパックのフォルダ
 */
val originalPackFolder = File("../../pack")

/**
 * 出力フォルダ
 */
val outFolder = File("../../out")

/**
 * ホモ特有のTMPフォルダ
 */
val tmpFolder = File("../../tmp")

/**
 * minecraftのリソースフォルダ
 */
val mcResFolder = File(tmpFolder, "minecraft_resource")

/**
 * パックフォルダ
 */
val packFolder = File(outFolder, "TexturePack")

/**
 * プラグインに渡すリソースのマッピングデータの出力先
 */
val mappingFile = File(outFolder, "pack_map.json")

/**
 * リソースフォルダー
 */
val resourcesFolder = File("../resources")

/**
 * テンプレートフォルダー
 */
val templatesFolder = File("../templates")

/* --- パック内のフォルダ/ファイル --- */

/**
 * パックのassetsディレクトリ
 */
val packAssetsDir = File(packFolder, "assets")


/* --- テンプレート --- */

/**
 * 基本的なアイテムモデルのテンプレート
 */
val basicFlatItemModelTemplate = JsonTemplate<SimpleLayer0ItemApplier>(templateModelFile("basic_flat_item.json"))

/**
 * 剣やピッケルなど道具のような持ち方をするアイテムモデルのテンプレート
 */
val handHeldItemModelTemplate = JsonTemplate<SimpleLayer0ItemApplier>(templateModelFile("handheld_item.json"))

/* ------------------------------------------------------------------------------------------------------------ */

println("処理開始")

/* --- 事前処理 --- */
stopWatch.reset()
stopWatch.start()


runBlocking {
    val outDirJob = scope.launch(Dispatchers.IO) {
        // 出力フォルダをクリア
        FileUtils.deleteDirectory(outFolder)

        // パックを出力フォルダへコピー
        syncMkdir(packFolder.parentFile)
        FileUtils.copyDirectory(originalPackFolder, packFolder)
    }

    val tmpDirJob = scope.launch(Dispatchers.IO) {
        // TMPフォルダをクリア
        FileUtils.deleteDirectory(tmpFolder)

        // Minecraftのクライアントjarをダウンロード
        syncMkdir(mcResFolder)
        val req = HttpRequest.newBuilder(URI.create(mcJarLink))
            .GET()
            .build()

        val res = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream())
        val mcJarFile = File(mcResFolder, "minecraft.jar")

        res.body().let {
            FNDataUtil.i2o(it, BufferedOutputStream(FileOutputStream(mcJarFile)))
        }

        // Minecraftのクライアントjarを解凍
        val mcJarIn = BufferedInputStream(FileInputStream(mcJarFile))
        val zipIn = ZipInputStream(mcJarIn)

        withContext(Dispatchers.Default) {
            zipIn.use {
                var entry = it.nextEntry

                while (entry != null) {

                    // 今のところモデルのみ展開
                    if (entry.name.startsWith("assets/minecraft/models/")) {
                        val path = Paths.get(mcResFolder.path, entry.name)
                        syncMkdir(path.parent.toFile())
                        Files.write(path, it.readAllBytes())
                    }

                    entry = it.nextEntry
                }
            }
        }
    }

    outDirJob.join()
    tmpDirJob.join()
}

val preProcTime = stopWatch.elapsed(TimeUnit.MILLISECONDS)
println("事前処理完了\t\t経過時間: ${preProcTime}ms")

/* --- タスク初期化 --- */

/**
 * 読み込み済みで更新する可能性があるリソース、生成先(正規表現)と生成するリソースホルダのマップ、非同期で更新することは未想定
 */
val resourceHolders = ConcurrentHashMap<ResourceLocationFile, ResourceHolder>()

/**
 * 実行するタスク
 */
val tasks = LinkedList<GenerateTask>()

/**
 * リソースマッピング
 */
val resourceMapping = ResourceMapping()

stopWatch.reset()
stopWatch.start()

// タスク初期化処理
initTasks()

/**
 * タスク初期化処理にかかった時間 (ms)
 */
val regTaskTime = stopWatch.elapsed(TimeUnit.MILLISECONDS)

println("タスク初期化完了\t経過時間: ${regTaskTime}ms")

/* --- タスク実行 --- */

stopWatch.reset()
stopWatch.start()


runBlocking {
    // 非同期タスク実行
    tasks.stream()
        .map {
            scope.launch {
                withContext(Dispatchers.Default) { it.run() } // タスク実行
                synchronized(resourceMapping) { it.post() } // ポスト処理
            }
        }
        .toList()
        .joinAll()
}

/**
 * タスクの処理にかかった時間 (ms)
 */
val runTaskTime = stopWatch.elapsed(TimeUnit.MILLISECONDS)

println("タスク処理完了\t経過時間: ${runTaskTime}ms")

/* --- 生成された結果の書き込み処理 --- */

stopWatch.reset()
stopWatch.start()

runBlocking {
    // 非同期リソースマッピング書き込み
    val resMappingJob = scope.launch(Dispatchers.Default) {
        val jo = JsonObject()
        resourceMapping.saveToJson(jo)
        saveJson(mappingFile, jo)
    }

    // 非同期リソース書き込み
    resourceHolders.entries.stream()
        .filter { it.value.isDirty() } // 更新されているリソースのみ書き込み
        .map {
            // IOだが、重いためDefaultで処理
            scope.launch(Dispatchers.Default) { it.value.saveToFile(it.key.packFile()) }
        }
        .toList()
        .joinAll()

    resMappingJob.join()
}

val genResTime = stopWatch.elapsed(TimeUnit.MILLISECONDS)
println("生成処理完了\t\t経過時間: ${genResTime}ms")

stopWatch.stop()

/* ------------------------------------------------------------------------------------------------------------ */

/* --- メソッド/クラス等 ---*/

/**
 * 生成タスク処理
 */
fun initTasks() {
    /* ここから下に記述してください */

    basicFlatItemModelTask("slime_ball", "test", resItemTexFile("test.png"))
    handheldItemModelTask("slime_ball", "test2", resItemTexFile("test_1.png"))
    // basicFlatItemModelTask("slime_ball", 1, resItemTexFile("test.png"))
    // handheldItemModelTask("slime_ball", 3, slLoc("item/test"))
    // injectItemModel("slime_ball", 2, slLoc("item/test_model"))

    /* ここから上に記述してください */
}

/**
 * 道具持ちアイテムモデル (マッピング経由でのみ使用を想定するモデルを生成)
 */
fun handheldItemModelTask(injectItemModelName: String, mappingId: String, textureFile: File) {
    val texLoc = textureCopyTask(textureFile)
    val modelLoc = handheldItemModelGenTask(slLoc("item/${FNStringUtil.removeExtension(textureFile.name)}"), texLoc)
    regTask(ModelNumberingInjectionTask(injectItemModelName, mappingId, modelLoc))
}

/**
 * 道具持ちアイテムモデル (指定されたファイルからコピーを行う)
 */
fun handheldItemModelTask(injectItemModelName: String, customModelNum: Int, textureLocation: ResourceLocation) {
    assertExistTexture(textureLocation)

    val modelLoc = handheldItemModelGenTask(textureLocation, textureLocation)
    injectModel(ResourceLocation("item/$injectItemModelName"), customModelNum, modelLoc)
}

/**
 * 道具持ちアイテムモデル (指定されたファイルからコピーを行う)
 */
fun handheldItemModelTask(injectItemModelName: String, customModelNum: Int, textureFile: File) {
    val texLoc = textureCopyTask(textureFile)
    val modelLoc = handheldItemModelGenTask(slLoc("item/${FNStringUtil.removeExtension(textureFile.name)}"), texLoc)
    injectModel(ResourceLocation("item/$injectItemModelName"), customModelNum, modelLoc)
}

/**
 * 道具持ちアイテムモデルの生成タスクを登録する、実際に登録できたモデルのロケーションを返す
 */
fun handheldItemModelGenTask(modelLocation: ResourceLocation, textureLocation: ResourceLocation): ResourceLocation {
    // 道具持ちモデル生成タスクを登録
    val modelLocFile = locationFileByModel(modelLocation)
    val modelLoc = reservationNumberingResourceHolders(modelLocFile).toModelLocation()
    regTask(ModelGenTask(handHeldItemModelTemplate, SimpleLayer0ItemApplier(textureLocation), modelLoc))
    return modelLoc
}

/**
 * 基本的な平面アイテムモデル (マッピング経由でのみ使用を想定するモデルを生成)
 */
fun basicFlatItemModelTask(injectItemModelName: String, mappingId: String, textureFile: File) {
    val texLoc = textureCopyTask(textureFile)
    val modelLoc = basicFlatItemModelGenTask(slLoc("item/${FNStringUtil.removeExtension(textureFile.name)}"), texLoc)
    regTask(ModelNumberingInjectionTask(injectItemModelName, mappingId, modelLoc))
}

/**
 * 基本的な平面アイテムモデル (指定されたテクスチャリソースロケーションのモデルを生成)
 */
fun basicFlatItemModelTask(injectItemModelName: String, customModelNum: Int, textureLocation: ResourceLocation) {
    assertExistTexture(textureLocation)

    val modelLoc = basicFlatItemModelGenTask(textureLocation, textureLocation)
    injectModel(ResourceLocation("item/$injectItemModelName"), customModelNum, modelLoc)
}

/**
 * 基本的な平面アイテムモデル (指定されたファイルからコピーを行う)
 */
fun basicFlatItemModelTask(injectItemModelName: String, customModelNum: Int, textureFile: File) {
    val texLoc = textureCopyTask(textureFile)
    val modelLoc = basicFlatItemModelGenTask(slLoc("item/${FNStringUtil.removeExtension(textureFile.name)}"), texLoc)
    injectModel(ResourceLocation("item/$injectItemModelName"), customModelNum, modelLoc)
}

/**
 * 指定されたテクスチャファイルをリソースへコピーを行い、コピー後のテクスチャロケーションを取得する
 */
fun textureCopyTask(textureFile: File): ResourceLocation {
    // テクスチャコピータスクを追加
    val texLocFile = locationFileByTexture(slLoc("item/${FNStringUtil.removeExtension(textureFile.name)}"))
    val texLoc = reservationNumberingResourceHolders(texLocFile).toTextureLocation()
    regTask(TextureCopyTask(textureFile, texLoc))
    return texLoc
}

/**
 * 基本的な平面アイテムモデルの生成タスクを登録する、実際に登録できたモデルのロケーションを返す
 */
fun basicFlatItemModelGenTask(modelLocation: ResourceLocation, textureLocation: ResourceLocation): ResourceLocation {
    // 平面モデル生成タスクを登録
    val modelLocFile = locationFileByModel(modelLocation)
    val modelLoc = reservationNumberingResourceHolders(modelLocFile).toModelLocation()
    regTask(ModelGenTask(basicFlatItemModelTemplate, SimpleLayer0ItemApplier(textureLocation), modelLoc))
    return modelLoc
}

/**
 * 対象のアイテムモデルにカスタムモデルを登録する
 */
fun injectItemModel(injectItemModelName: String, customModelNum: Int, injectionModel: ResourceLocation) {
    injectModel(ResourceLocation("item/$injectItemModelName"), customModelNum, injectionModel)
}

/**
 * 対象のモデルにカスタムモデルを登録する
 */
fun injectModel(targetModel: ResourceLocation, customModelNum: Int, injectionModel: ResourceLocation) {
    // 注入先モデルにモデルを登録
    val injectModelLocFile = locationFileByModel(targetModel)
    val injectModelHolder = resourceHolderLoadIfAbsent(injectModelLocFile, true) as JsonResourceHolder
    customModelInjection(injectModelHolder.resource, customModelNum, injectionModel)
    injectModelHolder.dirty = true
}

/**
 * タスク登録
 */
fun regTask(task: GenerateTask) {
    tasks += task
}


/**
 * 指定されたテクスチャロケーションにテクスチャが存在するか確認
 */
fun assertExistTexture(textureLocation: ResourceLocation) {
    val texLocFile = locationFileByTexture(textureLocation)
    if (!texLocFile.packFile().exists() && !texLocFile.mcResFile().exists()) {
        throw RuntimeException("テクスチャが存在しません {$textureLocation}")
    }
}

/**
 * リソースホルダを取得する、読み込まれていない場合は読み込み後に取得、非同期想定、存在しない場合Minecraftクライアントリソースから読み込ませることも可能
 */
fun resourceHolderLoadIfAbsent(locationFile: ResourceLocationFile, mcClientRes: Boolean): ResourceHolder {
    return resourceHolders.computeIfAbsent(locationFile) { loadResourceHolder(it, mcClientRes) }
}

/**
 * リソースホルダーを読み込む
 */
fun loadResourceHolder(locationFile: ResourceLocationFile, mcClientRes: Boolean): ResourceHolder {
    val packFile = locationFile.packFile()

    // 指定されたときのみ、パックが存在しなければMinecraftクライアントリソースを読み込む
    if (mcClientRes && !packFile.exists() && !packFile.isDirectory) {
        val mcResHolder = loadMcResourceHolder(locationFile)
        if (mcResHolder != null) {
            return mcResHolder
        }
    }

    if (!packFile.exists() || packFile.isDirectory) {
        throw RuntimeException("リソースが存在しない、もしくはディレクトリです: $locationFile")
    }

    val extension = FNStringUtil.getExtension(packFile.name)

    if (extension == "json") {
        return JsonResourceHolder(loadJson(packFile), false)
    } else if (extension == "png") {
        return ImageResourceHolder(ImageIO.read(packFile), false)
    } else {
        throw RuntimeException("想定されてないリソースホルダのファイルです")
    }
}

/**
 * Minecraftクライアントリソースからリソースホルダーを読み込む
 */
fun loadMcResourceHolder(locationFile: ResourceLocationFile): ResourceHolder? {
    val mcFile = locationFile.mcResFile()

    if (!mcFile.exists() || mcFile.isDirectory) {
        return null
    }

    val extension = FNStringUtil.getExtension(mcFile.name)

    if (extension == "json") {
        return JsonResourceHolder(loadJson(mcFile), false)
    } else if (extension == "png") {
        return ImageResourceHolder(ImageIO.read(mcFile), false)
    } else {
        return null
    }
}

/**
 * 重複した場合は採番してリソースホルダを予約する、実際に予約できたリソースホルダを返す
 */
fun reservationNumberingResourceHolders(locationFile: ResourceLocationFile): ResourceLocationFile {
    val locFile = autoPackNumbering(locationFile)
    reservationResourceHolders(locFile)
    return locFile
}

/**
 * 重複防止のためリソースホルダを予約する
 */
fun reservationResourceHolders(locationFile: ResourceLocationFile) {
    if (resourceHolders.containsKey(locationFile)) {
        throw RuntimeException("リソースファイルが重複しています")
    } else {
        resourceHolders[locationFile] = DummyResourceHolder
    }
}

fun syncMkdir(file: File) {
    synchronized(mkdirLock) { FNDataUtil.wishMkdir(file) }
}

/**
 * Jsonをファイルに保存する
 */
fun saveJson(file: File, json: JsonObject) {
    syncMkdir(file.parentFile)
    BufferedWriter(FileWriter(file)).use { gson.toJson(json, it) }
}

/**
 * Jsonをファイルから読み込む
 */
fun loadJson(file: File): JsonObject {
    if (!file.exists() || file.isDirectory) {
        throw RuntimeException("指定されたファイルは存在しないかディレクトリです")
    }

    val ret: JsonObject
    BufferedReader(FileReader(file)).use { ret = gson.fromJson(it, JsonObject::class.java) }
    return ret
}

/**
 * ShortLifeがネームスペースのリソースロケーションを取得
 */
fun slLoc(path: String): ResourceLocation {
    return ResourceLocation(slId, path)
}

/**
 * モデルのテンプレートファイル
 */
fun templateModelFile(fileName: String): File {
    val tempModelFile = File(templatesFolder, "models")
    return File(tempModelFile, fileName)
}

/**
 * リソースフォルダ内のアイテムテクスチャファイルを取得
 */
fun resItemTexFile(fileName: String): File {
    val texItemsFile = File(resourcesFolder, "items")
    return File(texItemsFile, fileName)
}

/**
 * リソースフォルダ内のGUIテクスチャファイルを取得
 */
fun resGuiTexFile(fileName: String): File {
    val texGuiFile = File(resourcesFolder, "gui")
    return File(texGuiFile, fileName)
}

/**
 * パックフォルダから相対的なファイルを取得
 */
fun packNameSpaceFile(nameSpace: String, path: Path): File {
    val nameSpaceFile = File(packAssetsDir, nameSpace)
    return nameSpaceFile.toPath().resolve(path).toFile()
}

/**
 * Minecraftクライアントリソースのassets/minecraft以下から相対的なファイルを取得
 */
fun mcResSpaceFile(path: Path): File {
    val nameSpaceFile = File(mcResFolder, "assets/minecraft")
    return nameSpaceFile.toPath().resolve(path).toFile()
}

/**
 * 出力されるパックで重複したリソースがある場合は、採番したリソース名に変更する、非同期は未想定
 */
fun autoPackNumbering(targetLocationFile: ResourceLocationFile): ResourceLocationFile {
    val loc = targetLocationFile.location

    val retPath = autoTextNumbering(loc.path) {
        val checkLoc = ResourceLocationFile(ResourceLocation(loc.nameSpace, it), targetLocationFile.extension)
        resourceHolders.containsKey(checkLoc) || checkLoc.packFile().exists()
    }

    val retLoc = ResourceLocation(loc.nameSpace, retPath)

    return ResourceLocationFile(retLoc, targetLocationFile.extension)
}

/**
 * 指定されたテキストが重複するか確認を行い、重複した場合は採番したテキストを取得する
 */
fun autoTextNumbering(targetText: String, duplicateChecker: Function<String, Boolean>): String {

    // 重複無し
    if (!duplicateChecker.apply(targetText)) {
        return targetText
    }

    var num = 1
    var numberingText = "${targetText}_${num}"

    // 重複しなくなるまで確認を続ける
    while (duplicateChecker.apply(numberingText)) {
        num++
        numberingText = "${targetText}_${num}"
    }

    return numberingText
}

/**
 * カスタムモデルを追加する
 */
fun customModelInjection(modelJson: JsonObject, customModelNum: Int, injectModelLocation: ResourceLocation) {
    val overridesJa: JsonArray

    if (modelJson.has("overrides")) {
        if (modelJson.get("overrides")?.isJsonArray == true) {
            /* 存在する場合は取得 */
            overridesJa = modelJson.getAsJsonArray("overrides")

            // カスタムモデルの番号重複確認
            overridesJa.forEach {
                val cmJo = requireNotNull(it.asJsonObject)
                val predicateJo = requireNotNull(cmJo.getAsJsonObject("predicate"))
                if (predicateJo.has("custom_model_data")) {
                    val cmdNum = predicateJo.getAsJsonPrimitive("custom_model_data").asInt
                    if (cmdNum == customModelNum) {
                        throw RuntimeException("カスタムモデルの番号が重複しています: ${cmJo.getAsJsonPrimitive("model")?.asString ?: "?"}(${cmdNum}) = ${injectModelLocation}(${customModelNum})")
                    }
                }
            }
        } else {
            /* Json配列以外が存在する場合はエラー */
            throw RuntimeException("Json配列以外のoverridesが存在します")
        }
    } else {
        /* 存在しない場合は追加 */
        overridesJa = JsonArray()
        modelJson.add("overrides", overridesJa)
    }

    // カスタムモデルJsonを追加
    val customModelJo = JsonObject()

    val predicateJo = JsonObject()
    predicateJo.addProperty("custom_model_data", customModelNum)
    customModelJo.add("predicate", predicateJo)

    customModelJo.addProperty("model", injectModelLocation.toString())

    overridesJa.add(customModelJo)

    // モデル番号順にソート
    val overridesJaList = overridesJa.deepCopy().sortedBy {
        it.asJsonObject.getAsJsonObject("predicate")
            .getAsJsonPrimitive("custom_model_data").asInt
    }
    overridesJa.removeAll { true }
    overridesJaList.forEach { overridesJa.add(it) }
}

/**
 * 未割当のカスタマイズモデル番号を取得、1から順に未割当箇所を返す、割り当てを返せない場合はnull
 */
fun getCustomModelUnassignedNumber(modelJson: JsonObject): Int? {
    if (modelJson.has("overrides")) {
        if (modelJson.get("overrides")?.isJsonArray == true) {
            val overridesJa = modelJson.getAsJsonArray("overrides")
            val assignedNums = LinkedList<Int>()

            // 割り当て済み番号確認
            overridesJa.forEach {
                val cmJo = requireNotNull(it.asJsonObject)
                val predicateJo = requireNotNull(cmJo.getAsJsonObject("predicate"))
                if (predicateJo.has("custom_model_data")) {
                    val cmdNum = predicateJo.getAsJsonPrimitive("custom_model_data").asInt
                    assignedNums.add(cmdNum)
                }
            }

            // 未割当番号取得
            var num = 1
            while (assignedNums.contains(num)) {
                num++
            }

            return num
        } else {
            /* Json配列以外のoverridesが存在する場合 */
            return null
        }
    } else {
        /* 割り当て無し */
        return 1
    }
}

/**
 * 生成タスク
 */
interface GenerateTask {
    /**
     * タスクを実行 (サスペンド)
     */
    suspend fun run()

    /**
     * タスク後の処理 (排他的に実行)
     */
    fun post()
}

/**
 * Jsonのテンプレート
 */
class JsonTemplate<T : TemplateApplier>(private val templateJsonFile: File) {
    private val templateJsonCache = Suppliers.memoize { loadJson(templateJsonFile) }

    /**
     * applierを使用してテンプレートをもとにJsonを作成
     */
    fun create(applier: T): JsonObject {
        val retJson = templateJsonCache.get().deepCopy()
        applier.apply(retJson)
        return retJson
    }
}

/**
 * テンプレートを適用するためのインターフェイス
 */
interface TemplateApplier {

    /**
     * テンプレートJsonに適用
     */
    fun apply(templateJson: JsonObject)
}

/**
 * リソース保持用クラス
 */
interface ResourceHolder {

    /**
     * ファイルに保存
     */
    fun saveToFile(file: File)

    /**
     * 元のファイルから更新されているかどうか
     */
    fun isDirty(): Boolean
}

/**
 * jsonリソース保持用クラス
 */
data class JsonResourceHolder(val resource: JsonObject, @Volatile var dirty: Boolean) : ResourceHolder {

    override fun saveToFile(file: File) {
        saveJson(file, this.resource)
    }

    override fun isDirty(): Boolean {
        return dirty
    }
}

/**
 * 画像リソース保持用クラス
 */
data class ImageResourceHolder(@Volatile var resource: BufferedImage, @Volatile var dirty: Boolean) : ResourceHolder {

    override fun saveToFile(file: File) {
        syncMkdir(file.parentFile)
        ImageIO.write(this.resource, "png", file)
    }

    override fun isDirty(): Boolean {
        return dirty
    }
}

/**
 * ダミーのリソース保持用クラス
 */
object DummyResourceHolder : ResourceHolder {
    override fun saveToFile(file: File) {
    }

    override fun isDirty(): Boolean {
        return false
    }
}

/**
 * リソースロケーション
 */
data class ResourceLocation(val nameSpace: String, val path: String) {
    private constructor(locationTexts: Array<String>) : this(locationTexts[0], locationTexts[1])
    constructor(locationText: String) : this(decomposeLocation(locationText))

    override fun toString(): String {
        // 容量削減のためminecraftを省略
        return if (this.nameSpace == mcId) {
            path
        } else {
            "${this.nameSpace}:${this.path}"
        }
    }
}

/**
 * ファイルとしてのリソースロケーション
 */
data class ResourceLocationFile(val location: ResourceLocation, val extension: String) {

    /**
     * パックディレクトリから相対的なファイルを取得
     */
    fun packFile(): File {
        return packNameSpaceFile(location.nameSpace, Path.of("${location.path}.${extension}"))
    }

    /**
     * Minecraftクライアントリソースから相対的なファイルを取得
     */
    fun mcResFile(): File {
        return mcResSpaceFile(Path.of("${location.path}.${extension}"))
    }

    /**
     * モデルのリソースロケーションへ変換
     */
    fun toModelLocation(): ResourceLocation {
        val modelLocSt = "models/"

        if (!location.path.startsWith(modelLocSt)) {
            throw RuntimeException("モデルのリソースロケーションへ変換できないパスです")
        }

        return ResourceLocation(location.nameSpace, location.path.substring(modelLocSt.length))
    }

    /**
     * テクスチャのリソースロケーションへ変換
     */
    fun toTextureLocation(): ResourceLocation {
        val modelLocSt = "textures/"

        if (!location.path.startsWith(modelLocSt)) {
            throw RuntimeException("テクスチャのリソースロケーションへ変換できないパスです")
        }

        return ResourceLocation(location.nameSpace, location.path.substring(modelLocSt.length))
    }
}

/**
 * モデルのリソースロケーションからロケーションファイルを取得
 */
fun locationFileByModel(modelLocation: ResourceLocation): ResourceLocationFile {
    val loc = ResourceLocation(modelLocation.nameSpace, "models/${modelLocation.path}")
    return ResourceLocationFile(loc, "json")
}

/**
 * テクスチャのリソースロケーションからロケーションファイルを取得
 */
fun locationFileByTexture(textureLocation: ResourceLocation): ResourceLocationFile {
    val loc = ResourceLocation(textureLocation.nameSpace, "textures/${textureLocation.path}")
    return ResourceLocationFile(loc, "png")
}

/**
 * リソースロケーションのテキストを分解する
 */
fun decomposeLocation(locationText: String): Array<String> {
    return if (!locationText.contains(":")) {
        arrayOf(mcId, locationText)
    } else {
        val strs = locationText.split(":")

        if (strs.size == 2) {
            arrayOf(strs[0], strs[1])
        } else {
            throw RuntimeException("「:」の数が不正です")
        }
    }
}

/**
 * プラグインからリソースの割り当てを確認するための、マッピングデータ、非同期は想定外
 */
class ResourceMapping {

    /**
     * バージョン
     */
    private val packMapVersion = 0

    /**
     * カスタムモデルのマッピング情報
     */
    private val customModelMapping = HashMap<String, CustomModelItemEntry>()

    /**
     * カスタムモデルのマッピング情報を追加
     */
    fun addCustomModelMapping(id: String, model: ResourceLocation, customModelNumber: Int) {
        customModelMapping[id] = CustomModelItemEntry(model, customModelNumber)
    }

    /**
     * マッピング情報をJsonへ保存
     */
    fun saveToJson(json: JsonObject) {
        json.addProperty("version", packMapVersion)

        val customModelJo = JsonObject()

        // カスタムモデルマッピングを出力
        customModelMapping.forEach { id, modelEntry ->
            val entryJo = JsonObject()
            entryJo.addProperty("model", modelEntry.model.toString())
            entryJo.addProperty("num", modelEntry.customModelNumber)
            customModelJo.add(id, entryJo)
        }

        json.add("custom_model", customModelJo)
    }
}

data class CustomModelItemEntry(val model: ResourceLocation, val customModelNumber: Int)

/**
 * 基本的なアイテムモデルのテンプレート適用クラス
 */
class SimpleLayer0ItemApplier(private val textureLocation: ResourceLocation) : TemplateApplier {
    override fun apply(templateJson: JsonObject) {
        val texJo = templateJson.getAsJsonObject("textures")
        texJo.addProperty("layer0", this.textureLocation.toString())
    }
}

/**
 * テクスチャをコピーするタスク
 */
class TextureCopyTask(private val fromTextureFile: File, private val toTextureLocation: ResourceLocation) :
    GenerateTask {
    @Volatile
    private var textureImage: BufferedImage? = null

    override suspend fun run() {
        // テクスチャを読み込む
        withContext(Dispatchers.IO) {
            textureImage = ImageIO.read(fromTextureFile)
        }
    }

    override fun post() {
        // リソースホルダへ登録
        val loc = ResourceLocation(toTextureLocation.nameSpace, "textures/${toTextureLocation.path}")
        resourceHolders[ResourceLocationFile(loc, "png")] = ImageResourceHolder(requireNotNull(textureImage), true)
    }
}

/**
 * モデル生成タスク、生成したモデルをターゲットの場所へ配置
 */
class ModelGenTask<T : TemplateApplier>(
    private val template: JsonTemplate<T>,
    private val applier: T,
    private val targetLocation: ResourceLocation
) : GenerateTask {
    @Volatile
    private var modelJson: JsonObject? = null

    override suspend fun run() {
        modelJson = withContext(Dispatchers.Default) { template.create(applier) }
    }

    override fun post() {
        // リソースホルダへ登録
        val loc = ResourceLocation(targetLocation.nameSpace, "models/${targetLocation.path}")
        resourceHolders[ResourceLocationFile(loc, "json")] = JsonResourceHolder(requireNotNull(modelJson), true)
    }
}

class ModelNumberingInjectionTask(
    private val injectItemModelName: String,
    private val mappingId: String,
    private val model: ResourceLocation
) :
    GenerateTask {
    override suspend fun run() {
        // 処理なし
    }

    override fun post() {
        // 注入先モデルにモデルを登録
        val injectModelLoc = ResourceLocation("item/$injectItemModelName")
        val injectModelLocFile = locationFileByModel(injectModelLoc)
        val injectModelHolder = resourceHolderLoadIfAbsent(injectModelLocFile, true) as JsonResourceHolder

        val modelNum = getCustomModelUnassignedNumber(injectModelHolder.resource)
            ?: throw RuntimeException("カスタムモデル番号を割り当てることができませんでした: $injectItemModelName")

        customModelInjection(injectModelHolder.resource, modelNum, model)
        injectModelHolder.dirty = true

        // マッピングに登録
        resourceMapping.addCustomModelMapping(mappingId, injectModelLoc, modelNum)
    }
}
