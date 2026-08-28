#!/usr/bin/env ruby

require "json"
require "rexml/document"

MapValue = Struct.new(:entries)
SequenceValue = Struct.new(:items)
Entry = Struct.new(:key, :value, :comments)
Item = Struct.new(:value, :comments)

LIST_CHILD_EXCEPTIONS = {
  "additionalClasspathDependencies" => "additionalClasspathDependency",
  "annotationProcessorPaths" => "path",
  "testResources" => "testResource"
}.freeze

def comment_lines(comment)
  comment.to_s.lines.map(&:strip).drop_while(&:empty?).reverse.drop_while(&:empty?).reverse
end

def singularize(name)
  return name.sub(/ies\z/, "y") if name.end_with?("ies")
  return name[0...-1] if name.end_with?("s")

  name
end

def list_wrapper?(element, children)
  return false if children.empty?

  child_names = children.map(&:name).uniq
  return false unless child_names.length == 1

  expected_child = LIST_CHILD_EXCEPTIONS.fetch(element.name, singularize(element.name))
  child_names.first == expected_child
end

def nonstandard_configuration_list?(element, children, inside_configuration)
  return false unless inside_configuration || element.name == "configuration"
  return false unless list_wrapper?(element, children)

  children.first.name != singularize(element.name)
end

def element_text(element)
  element.children
    .select { |child| child.is_a?(REXML::Text) }
    .map(&:value)
    .join
    .strip
end

def convert_element(element, inside_configuration = false)
  child_elements = element.children.select { |child| child.is_a?(REXML::Element) }
  configuration_value = inside_configuration || element.name == "configuration"

  if child_elements.empty? && element.attributes.empty?
    text = element_text(element)
    return MapValue.new([]) if configuration_value && text.empty?
    text = text.sub(/pom\.xml\z/, "pom.yaml") if element.name == "relativePath"

    return text
  end

  if list_wrapper?(element, child_elements) && element.attributes.empty?
    if nonstandard_configuration_list?(element, child_elements, inside_configuration)
      if child_elements.length != 1
        raise "Mason 0.3.0 cannot represent repeated <#{child_elements.first.name}> " \
          "elements under <#{element.name}> without changing their XML names"
      end

      comments = element.children
        .take_while { |child| !child.is_a?(REXML::Element) }
        .select { |child| child.is_a?(REXML::Comment) }
        .flat_map { |child| comment_lines(child) }
      return MapValue.new([
        Entry.new(
          child_elements.first.name,
          convert_element(child_elements.first, configuration_value),
          comments
        )
      ])
    end

    pending_comments = []
    items = []
    element.children.each do |child|
      if child.is_a?(REXML::Comment)
        pending_comments.concat(comment_lines(child))
      elsif child.is_a?(REXML::Element)
        items << Item.new(convert_element(child, configuration_value), pending_comments)
        pending_comments = []
      end
    end
    return SequenceValue.new(items)
  end

  entries = []
  element.attributes.each_attribute do |attribute|
    next if attribute.prefix == "xmlns" || attribute.name == "xmlns" || attribute.expanded_name == "xmlns"
    next if attribute.prefix == "xsi" && attribute.name == "schemaLocation"

    if configuration_value
      raise "Mason 0.3.0 cannot represent plugin configuration attribute " \
        "#{attribute.name} on <#{element.name}>"
    end
    entries << Entry.new(attribute.expanded_name, attribute.value, [])
  end

  pending_comments = []
  element.children.each do |child|
    if child.is_a?(REXML::Comment)
      pending_comments.concat(comment_lines(child))
    elsif child.is_a?(REXML::Element)
      entries << Entry.new(
        child.name,
        convert_element(child, configuration_value),
        pending_comments
      )
      pending_comments = []
    end
  end

  text = element_text(element)
  entries << Entry.new("#text", text, []) unless text.empty?
  MapValue.new(entries)
end

def plain_scalar?(value)
  return false if value.empty?
  return false unless value.match?(/\A[A-Za-z0-9_.$\/{\}()+,=*-]+\z/)
  return false if value.match?(/\A[-*]/)
  return false if value.match?(/\A(?:true|false|null|yes|no|on|off|~)\z/i)
  return false if value.match?(/\A[-+]?\d+(?:\.\d+)?\z/)

  true
end


def render_scalar(value)
  plain_scalar?(value) ? value : JSON.generate(value)
end

def render_key(key)
  key.match?(/\A[A-Za-z0-9_.-]+\z/) ? key : JSON.generate(key)
end


def emit_comments(output, comments, indent)
  comments.each do |comment|
    if comment.empty?
      output << "\n"
    else
      output << "#{" " * indent}# #{comment}\n"
    end
  end
end

def inline_value?(value)
  value.is_a?(String) || (value.is_a?(MapValue) && value.entries.empty?)
end

def emit_entry(output, entry, indent, prefix = nil)
  emit_comments(output, entry.comments, indent) unless prefix
  line_prefix = prefix || (" " * indent)
  output << "#{line_prefix}#{render_key(entry.key)}:"
  if entry.value.is_a?(String)
    output << " #{render_scalar(entry.value)}\n"
  elsif entry.value.is_a?(MapValue) && entry.value.entries.empty?
    output << " {}\n"
  else
    output << "\n"
    emit_value(output, entry.value, indent + 2)
  end
end

def emit_map(output, map, indent)
  map.entries.each { |entry| emit_entry(output, entry, indent) }
end

def emit_sequence(output, sequence, indent)
  sequence.items.each do |item|
    emit_comments(output, item.comments, indent)
    value = item.value
    if value.is_a?(String)
      output << "#{" " * indent}- #{render_scalar(value)}\n"
    elsif value.is_a?(MapValue) && value.entries.empty?
      output << "#{" " * indent}- {}\n"
    elsif value.is_a?(MapValue) && !value.entries.empty? &&
        value.entries.first.comments.empty? && inline_value?(value.entries.first.value)
      first, *remaining = value.entries
      emit_entry(output, first, indent + 2, "#{" " * indent}- ")
      remaining.each { |entry| emit_entry(output, entry, indent + 2) }
    else
      output << "#{" " * indent}-\n"
      emit_value(output, value, indent + 2)
    end
  end
end

def emit_value(output, value, indent)
  if value.is_a?(MapValue)
    emit_map(output, value, indent)
  elsif value.is_a?(SequenceValue)
    emit_sequence(output, value, indent)
  else
    raise "Unsupported YAML value: #{value.class}"
  end
end

unless ARGV.length == 2
  warn "Usage: #{$PROGRAM_NAME} <pom.xml> <pom.yaml>"
  exit 2
end

source, destination = ARGV
document = REXML::Document.new(File.read(source, encoding: "UTF-8"))
project = document.root
raise "Expected a Maven project root in #{source}" unless project&.name == "project"

model = convert_element(project)
output = +""
emit_value(output, model, 0)
File.write(destination, output, mode: "w", encoding: "UTF-8")
