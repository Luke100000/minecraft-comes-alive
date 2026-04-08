param(
    [string]$RecipeDir = (Join-Path $PSScriptRoot '..\common\src\main\resources\data\mca\recipe')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Convert-ToMutableJsonNode {
    param(
        [Parameter(Mandatory = $true)]
        [AllowNull()]
        [object]$Node
    )

    if ($null -eq $Node) {
        return $null
    }

    if ($Node -is [System.Management.Automation.PSCustomObject]) {
        $converted = [ordered]@{}
        foreach ($property in $Node.PSObject.Properties) {
            $converted[$property.Name] = Convert-ToMutableJsonNode -Node $property.Value
        }
        return $converted
    }

    if ($Node -is [System.Collections.IEnumerable] -and $Node -isnot [string] -and $Node -isnot [System.Collections.IDictionary]) {
        $converted = New-Object System.Collections.ArrayList
        foreach ($entry in $Node) {
            [void]$converted.Add((Convert-ToMutableJsonNode -Node $entry))
        }
        return ,$converted.ToArray()
    }

    return $Node
}

function Convert-LegacyIngredient {
    param(
        [Parameter(Mandatory = $true)]
        [AllowNull()]
        [object]$Node,

        [Parameter(Mandatory = $true)]
        [ref]$Changed
    )

    if ($null -eq $Node) {
        return $null
    }

    if ($Node -is [System.Collections.IDictionary]) {
        $keys = @($Node.Keys)
        if ($keys.Count -eq 1) {
            if ($keys[0] -eq 'item') {
                $Changed.Value = $true
                return [string]$Node['item']
            }

            if ($keys[0] -eq 'tag') {
                $Changed.Value = $true
                return '#' + [string]$Node['tag']
            }
        }

        return $Node
    }

    if ($Node -is [System.Collections.IEnumerable] -and $Node -isnot [string]) {
        $converted = New-Object System.Collections.ArrayList
        foreach ($entry in $Node) {
            [void]$converted.Add((Convert-LegacyIngredient -Node $entry -Changed $Changed))
        }

        if ($converted.Count -eq 1) {
            $Changed.Value = $true
            return $converted[0]
        }

        return ,$converted.ToArray()
    }

    return $Node
}

$resolvedRecipeDir = Resolve-Path -LiteralPath $RecipeDir
$updatedFiles = New-Object System.Collections.Generic.List[string]

Get-ChildItem -LiteralPath $resolvedRecipeDir -Filter '*.json' -File | Sort-Object FullName | ForEach-Object {
    $path = $_.FullName
    $raw = Get-Content -Raw -LiteralPath $path
    $recipe = Convert-ToMutableJsonNode -Node ($raw | ConvertFrom-Json)
    $changed = $false

    if ($recipe.Contains('ingredient')) {
        $recipe['ingredient'] = Convert-LegacyIngredient -Node $recipe['ingredient'] -Changed ([ref]$changed)
    }

    if ($recipe.Contains('ingredients')) {
        $normalizedIngredients = New-Object System.Collections.ArrayList
        foreach ($ingredient in $recipe['ingredients']) {
            [void]$normalizedIngredients.Add((Convert-LegacyIngredient -Node $ingredient -Changed ([ref]$changed)))
        }
        $recipe['ingredients'] = ,$normalizedIngredients.ToArray()
    }

    if ($recipe.Contains('key')) {
        foreach ($symbol in @($recipe['key'].Keys)) {
            $recipe['key'][$symbol] = Convert-LegacyIngredient -Node $recipe['key'][$symbol] -Changed ([ref]$changed)
        }
    }

    if ($changed) {
        $json = $recipe | ConvertTo-Json -Depth 100
        [System.IO.File]::WriteAllText($path, $json + [Environment]::NewLine)
        $updatedFiles.Add($path) | Out-Null
    }
}

$updatedFiles
