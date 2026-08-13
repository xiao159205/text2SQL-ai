const LINE_BREAK_CLAUSES = [
  'UNION ALL',
  'LEFT JOIN',
  'RIGHT JOIN',
  'INNER JOIN',
  'FULL JOIN',
  'CROSS JOIN',
  'GROUP BY',
  'ORDER BY',
  'UNION',
  'FROM',
  'WHERE',
  'HAVING',
  'LIMIT',
  'OFFSET',
]

const isIdentifierChar = (char) => !!char && /[A-Za-z0-9_]/.test(char)

function clauseAt(sql, index) {
  if (isIdentifierChar(sql[index - 1])) return null
  return LINE_BREAK_CLAUSES.find((clause) => {
    if (sql.slice(index, index + clause.length).toUpperCase() !== clause) return false
    return !isIdentifierChar(sql[index + clause.length])
  }) || null
}

/** 只格式化展示文本，不改动字符串常量和反引号中的内容。 */
export function formatSql(sql) {
  if (typeof sql !== 'string') return ''

  let output = ''
  let quote = null
  for (let index = 0; index < sql.length;) {
    const char = sql[index]

    if (quote) {
      output += char
      if (char === quote) {
        if (sql[index + 1] === quote) {
          output += sql[index + 1]
          index += 2
          continue
        }
        quote = null
      }
      index += 1
      continue
    }

    if (char === "'" || char === '"' || char === '`') {
      quote = char
      output += char
      index += 1
      continue
    }

    const clause = clauseAt(sql, index)
    if (clause) {
      output = output.trimEnd()
      if (output) output += '\n'
      output += sql.slice(index, index + clause.length)
      index += clause.length
      continue
    }

    output += char
    index += 1
  }

  return output.trim()
}
