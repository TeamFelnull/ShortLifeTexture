# ShortLifeTexture

GitKraken上でタグを作成してPushToOriginをすることでサーバーテクスチャが更新されます

## リソースの追加方法

### カスタムテクスチャアイテム

独自のテクスチャを使用したアイテムを追加するには以下の通りです。

1. 追加したいカスタムテクスチャをリソースのテクスチャディレクトリに配置する
2. 対象のテクスチャを使用したアイテムモデルを作成する
3. 2で作成したモデルの適用先とカスタムモデル番号を決め、モデルのJsonを編集する

実際にゲーム内でカスタムテクスチャアイテムを取得する場合は以下のコマンドを実行してください。  
`/give @p アイテムID{CustomModelData:カスタムモデル番号}`

#### 例

テクスチャ(`example_item.png`)を使用した平面アイテムを、スライムボールのカスタムモデル1番に追加する方法:

1. `example_item.png`を`pack\assets\shortlife\textures\item`フォルダ内に配置する
2. `generate\templates\models\basic_flat_item.json`を`pack\assets\shortlife\models\item`
   内にコピーして`example_item.json`に名前を変更する
3. `example_item.json`をメモ帳などで開き`"layer0": ""`と書かれている箇所を`"layer0": "shortlife:item/example_item"`
   に変更して保存する
4. `pack\assets\minecraft\models\item`の`slime_ball.json`
   を開き、既に存在する他のモデルを参考に`"custom_model_data": 1`,` "model": "shortlife:item/example_item"`として追記する
5. ゲーム内にてテクスチャを読み込み、`/give @p minecraft:slime_ball{CustomModelData:1}`を実行してモデルが適用されているか確認を行う

#### スクリプトを使用した方法

スクリプト(`generate\scripts\generator.main.kts`)を使用すると、カスタムテクスチャアイテムの登録を半自動化できます。

スクリプトを使用した[例](#例)と同じカスタムモデルの登録方法:

1. `example_item.png`を`pack\assets\shortlife\textures\item`フォルダ内に配置する
2. スクリプトファイルの`/* ここから下に記述してください */`と`/* ここから上に記述してください */`
   の間に`basicFlatItemModelTask("slime_ball", 1, slLoc("item/example_item"))`を追記する

```kotlin
/**
 * 生成タスク処理
 */
fun initTasks() {
    /* ここから下に記述してください */

    // basicFlatItemModelTask(カスタムモデルの登録先, カスタムモデル番号, テクスチャの場所)
    // スライムボールのカスタムモデル1番に、shortlife:item/example_itemのテクスチャを適用した平面モデルアイテムを登録
    basicFlatItemModelTask("slime_ball", 1, slLoc("item/example_item"))

    /* ここから上に記述してください */
}
```

スクリプトはリリース時にworkflowで実行されます。  
ローカルで実行してテストしたい場合は、IntellijIDEAなどで実行してください。

### 参考先

- [Minecraft公式Wiki](https://ja.minecraft.wiki/w/%E3%83%A2%E3%83%87%E3%83%AB)
- [スクリプト記述言語Kotlinのドキュメント](https://kotlinlang.org/docs/getting-started.html)